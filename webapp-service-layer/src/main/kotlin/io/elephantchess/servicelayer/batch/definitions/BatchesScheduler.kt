package io.elephantchess.servicelayer.batch.definitions

import io.elephantchess.config.AppConfig
import io.elephantchess.servicelayer.metrics.MetricsLogger
import io.elephantchess.servicelayer.services.PodService
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRate
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class BatchesScheduler(
    appConfig: AppConfig,
    schedules: List<BatchSchedule<out Batch>>,
    private val podService: PodService,
    refresherScope: CoroutineScope,
    private val logger: KLogger,
) {

    private val disabledBatches = appConfig.disabledBatches
    private val isDockerized = appConfig.isDockerized

    private val jobs = mutableListOf<Job>()

    init {
        schedules
            .filter { entry -> disabledBatches.contains(entry.name()) }
            .forEach { entry ->
                logger.info { "batch ${entry.name()} disabled, not scheduling" }
            }

        schedules
            .filterNot { batch -> disabledBatches.contains(batch.name()) }
            .forEach { batch ->
                if (disabledBatches.contains(batch.name())) {
                    logger.info { "batch ${batch.name()} disabled, not scheduling" }
                } else {
                    logger.info { "scheduling batch ${batch.name()}" }

                    jobs += launchAtFixedRate(
                        scope = refresherScope,
                        period = batch.period,
                        initialDelay = batch.delay,
                        action = {
                            podService.findPod()?.let { pod ->
                                when (batch.batch) {
                                    is ShardedBatch<*> -> {
                                        try {
                                            logger.debug { "running ${batch.name()}" }
                                            batch.batch.run(pod)
                                        } catch (e: Exception) {
                                            logger.error(e) { "error running batch ${batch.name()}" }
                                        }
                                    }

                                    is SinglePodBatch -> {
                                        if (pod.index == batch.batch.podNumber) {
                                            try {
                                                logger.debug { "running ${batch.name()}" }
                                                batch.batch.run()
                                            } catch (e: Exception) {
                                                logger.error(e) { "error running batch ${batch.name()}" }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )

                }
            }

        val metricsLogger = MetricsLogger()
        jobs += launchAtFixedRate(
            scope = refresherScope,
            period = 2.hours,
            initialDelay = 5.minutes,
            action = {
                metricsLogger.logAllMetrics()
                if (isDockerized) {
                    // log pod names
                    try {
                        logger.info {
                            runBlocking { "pods from db: ${podService.listPodNamesFromDb()}" }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "error listing pods" }
                    }

                    // log deploy time
                    try {
                        val lastRedeployTime = podService.getLastRedeployTime()
                        logger.info { "deploy time: $lastRedeployTime" }
                    } catch (e: Exception) {
                        logger.error(e) { "error finding deploy time" }
                    }
                }
            })
    }

    fun cancel() {
        jobs.forEach { job -> job.cancel() }
    }

}
