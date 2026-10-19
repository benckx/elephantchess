package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.servicelayer.model.Pod
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRate
import io.elephantchess.utils.di.KoinSingleton
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.lang.management.ManagementFactory
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@KoinSingleton(eager = true)
class PodService(appConfig: AppConfig, refresherScope: CoroutineScope, private val logger: KLogger) {

    private val isDockerized = appConfig.isDockerized

    // StatefulSet identity, injected via the downward API (see k8s/statefulset.yaml in the deploy repo).
    // Each pod derives its shard index and the total shard count directly from these env vars, so no
    // Kubernetes API client or "kubernetes_pod" DB registry is needed.
    private val podNameEnv: String? = System.getenv("POD_NAME")?.takeIf { it.isNotBlank() }
    private val podCountEnv: Int? = System.getenv("POD_COUNT")?.toIntOrNull()

    private val jobs = mutableListOf<Job>()

    init {
        if (isDockerized) {
            logger.info { "pod identity from StatefulSet env -> name=$podNameEnv, count=$podCountEnv" }
        }

        if (logger.isDebugEnabled()) {
            jobs += launchAtFixedRate(refresherScope, initialDelay = 20.seconds, period = 2.minutes) {
                logger.debug { "current pod -> ${findPod()}" }
            }
        }
    }

    fun getLastRedeployTime(): Instant {
        // no Deployment/StatefulSet object is queried; the JVM start time is a good proxy for when
        // this pod was last (re)deployed
        return Instant.fromEpochMilliseconds(ManagementFactory.getRuntimeMXBean().startTime)
    }

    fun findPod(): Pod? {
        if (!isDockerized) {
            return Pod(0, 1)
        }

        val index = podOrdinalFromEnv()
        val total = podCountEnv
        return if (index != null && total != null && index in 0 until total) {
            Pod(index, total)
        } else {
            logger.warn { "invalid StatefulSet pod identity -> name=$podNameEnv, count=$podCountEnv" }
            null
        }
    }

    // StatefulSet pod names are stable ordinals, e.g. "elephantchess-webapp-0" -> index 0
    private fun podOrdinalFromEnv(): Int? =
        podNameEnv?.substringAfterLast('-')?.toIntOrNull()

    fun cancel() {
        jobs.forEach { job -> job.cancel() }
    }
}
