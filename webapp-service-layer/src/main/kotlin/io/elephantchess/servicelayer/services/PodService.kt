package io.elephantchess.servicelayer.services

import io.elephantchess.utils.di.KoinSingleton

import io.elephantchess.config.AppConfig
import io.elephantchess.db.services.PodDaoService
import io.elephantchess.db.utils.toUtcInstant
import io.elephantchess.servicelayer.model.Pod
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRate
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRateStartImmediately
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@KoinSingleton(eager = true)
class PodService(
    appConfig: AppConfig,
    private val podDaoService: PodDaoService,
    refresherScope: CoroutineScope,
    private val logger: KLogger,
) {

    private val isDockerized = appConfig.isDockerized
    private var expectedNbrOfPods: Int? = null
    private val client by lazy { KubernetesClientBuilder().build() }

    // StatefulSet identity, injected via the downward API (see k8s/statefulset.yaml in the deploy repo).
    // When present, the pod derives its shard index/count from these env vars and never touches the
    // Kubernetes API or the "kubernetes_pod" table. When absent (e.g. the legacy Deployment), we fall
    // back to the k8s-client + DB registry below, so this change is backward compatible during the
    // Deployment -> StatefulSet transition.
    private val podNameEnv: String? = System.getenv("POD_NAME")?.takeIf { it.isNotBlank() }
    private val podCountEnv: Int? = System.getenv("POD_COUNT")?.toIntOrNull()
    private val statefulSetMode: Boolean = isDockerized && podNameEnv != null && podCountEnv != null

    private val jobs = mutableListOf<Job>()

    init {
        when {
            !isDockerized -> {
                expectedNbrOfPods = 1
            }

            statefulSetMode -> {
                expectedNbrOfPods = podCountEnv
                logger.info { "pod identity from StatefulSet env -> name=$podNameEnv, count=$podCountEnv" }
            }

            else -> {
                jobs += launchAtFixedRateStartImmediately(refresherScope, REFRESH_RATE_SECONDS.seconds) {
                    val podName = getPodName()
                    if (podName.startsWith("$DEPLOYMENT_NAME-")) {
                        podDaoService.insertOrUpdate(podName)
                    }
                }

                jobs += launchAtFixedRateStartImmediately(refresherScope, 5.minutes) {
                    updateExpectedNbrOfPods()
                }
            }
        }

        if (logger.isDebugEnabled()) {
            jobs += launchAtFixedRate(refresherScope, initialDelay = 20.seconds, period = 2.minutes) {
                logger.debug {
                    runBlocking {
                        "current pod -> ${findPod()}"
                    }
                }
            }
        }
    }

    suspend fun listPodNamesFromDb(): List<String> {
        return when {
            statefulSetMode -> podNamesFromEnv()
            isDockerized -> {
                expectedNbrOfPods
                    ?.let { number -> podDaoService.listLastPodNames(number) }
                    .orEmpty()
                    .sorted()
            }

            else -> podDaoService.listLastPodNames(2)
        }
    }

    fun getLastRedeployTime(): Instant? {
        if (statefulSetMode) {
            // no Deployment object to query; the JVM start time is a good proxy for when this
            // StatefulSet pod was last (re)deployed
            return Instant.fromEpochMilliseconds(ManagementFactory.getRuntimeMXBean().startTime)
        }

        return findDeployment()?.let { deployment ->
            deployment
                .status
                ?.conditions
                ?.toList()
                .orEmpty()
                .filterNotNull()
                .firstOrNull { it.type == "Progressing" && it.reason == "NewReplicaSetAvailable" }
                ?.lastUpdateTime
                ?.let { raw ->
                    java.time.LocalDateTime.parse(raw, ISO_DATE_TIME).toUtcInstant()
                }
        }
    }

    suspend fun findPod(): Pod? {
        if (!isDockerized) {
            return Pod(0, 1)
        }

        if (statefulSetMode) {
            val index = podOrdinalFromEnv()
            val total = podCountEnv
            return if (index != null && total != null && index in 0 until total) {
                Pod(index, total)
            } else {
                logger.warn { "invalid StatefulSet pod identity -> name=$podNameEnv, count=$podCountEnv" }
                null
            }
        }

        val duration = (REFRESH_RATE_SECONDS + 10).seconds
        val allPods = podDaoService.listAllPodNamesWithin(duration)

        return if (expectedNbrOfPods != null) {
            if (allPods.size != expectedNbrOfPods) {
                logger.warn { "not the expected number of pods -> ${allPods.size} != $expectedNbrOfPods (normal during re-deploys)" }
                null
            } else {
                val podIndex = allPods.indexOf(getPodName())
                Pod(podIndex, allPods.size)
            }
        } else {
            logger.warn { "expectedNbrOfPods is not known" }
            null
        }
    }

    // StatefulSet pod names are stable ordinals, e.g. "elephantchess-webapp-0" -> index 0
    private fun podOrdinalFromEnv(): Int? {
        return podNameEnv?.substringAfterLast('-')?.toIntOrNull()
    }

    private fun podNamesFromEnv(): List<String> {
        val total = podCountEnv ?: return emptyList()
        val prefix = podNameEnv?.substringBeforeLast('-') ?: return emptyList()
        return (0 until total).map { index -> "$prefix-$index" }
    }

    private fun updateExpectedNbrOfPods() {
        fetchNumberOfReplicas()?.let { replicas ->
            if (expectedNbrOfPods != replicas) {
                logger.warn { "expectedNbrOfPods to update from $expectedNbrOfPods -> $replicas" }
                expectedNbrOfPods = replicas
            }
        }
    }

    private fun fetchNumberOfReplicas(): Int? {
        try {
            findDeployment()?.let { deployment ->
                return deployment.spec.replicas
            }
        } catch (e: Exception) {
            logger.error { "error reading deployment: $e" }
        }

        return null
    }

    private fun findDeployment(): Deployment? {
        return client
            .apps()
            .deployments()
            .inNamespace("default")
            .list()
            .items
            .toList()
            .filterNotNull()
            .find { it.metadata.name == DEPLOYMENT_NAME }
    }

    private fun getPodName(): String {
        return InetAddress.getLocalHost().hostName
    }

    fun cancel() {
        jobs.forEach { job -> job.cancel() }
    }

    private companion object {

        const val REFRESH_RATE_SECONDS = 30
        const val DEPLOYMENT_NAME = "elephantchess-webapp"

    }

}
