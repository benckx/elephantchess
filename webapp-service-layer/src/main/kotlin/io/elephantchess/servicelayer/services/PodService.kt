package io.elephantchess.servicelayer.services

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
import java.net.InetAddress
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PodService(
    appConfig: AppConfig,
    private val podDaoService: PodDaoService,
    refresherScope: CoroutineScope,
    private val logger: KLogger,
) {

    private val isDockerized = appConfig.isDockerized
    private var expectedNbrOfPods: Int? = null
    private val client by lazy { KubernetesClientBuilder().build() }

    private val jobs = mutableListOf<Job>()

    init {
        if (isDockerized) {
            jobs += launchAtFixedRateStartImmediately(refresherScope, REFRESH_RATE_SECONDS.seconds) {
                val podName = getPodName()
                if (podName.startsWith("$DEPLOYMENT_NAME-")) {
                    podDaoService.insertOrUpdate(podName)
                }
            }

            jobs += launchAtFixedRateStartImmediately(refresherScope, 5.minutes) {
                updateExpectedNbrOfPods()
            }
        } else {
            expectedNbrOfPods = 1
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
        return if (isDockerized) {
            expectedNbrOfPods
                ?.let { number -> podDaoService.listLastPodNames(number) }
                .orEmpty()
                .sorted()
        } else {
            podDaoService.listLastPodNames(2)
        }
    }

    fun getLastRedeployTime(): Instant? {
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
        return if (isDockerized) {
            val duration = (REFRESH_RATE_SECONDS + 10).seconds
            val allPods = podDaoService.listAllPodNamesWithin(duration)

            if (expectedNbrOfPods != null) {
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
        } else {
            return Pod(0, 1)
        }
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
        const val DEPLOYMENT_NAME = "xiangqi-webapp"

    }

}
