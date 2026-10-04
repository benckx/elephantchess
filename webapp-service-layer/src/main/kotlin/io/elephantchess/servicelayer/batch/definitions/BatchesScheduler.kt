package io.elephantchess.servicelayer.batch.definitions

import io.elephantchess.config.AppConfig
import io.elephantchess.servicelayer.metrics.MetricsLogger
import io.elephantchess.servicelayer.services.PodService
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRate
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.util.reflect.instanceOf
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
            .filter { entry -> disabledBatches.contains(entry.batchName) }
            .forEach { entry ->
                logger.info { "batch ${entry.batchName} disabled, not scheduling" }
            }

        schedules
            .filterNot { schedule -> disabledBatches.contains(schedule.batchName) }
            .filterNot { schedule -> schedule.batch.instanceOf(SinglePodBatch::class) }
            .forEach { schedule ->
                val podNumber = (schedule.batch as SinglePodBatch).podNumber
                logger.info { "${schedule.batchName} will be scheduled on pod $podNumber" }
            }

        schedules
            .filterNot { schedule -> disabledBatches.contains(schedule.batchName) }
            .forEach { schedule ->
                if (disabledBatches.contains(schedule.batchName)) {
                    logger.info { "batch ${schedule.batchName} disabled, not scheduling" }
                } else {
                    logger.info { "scheduling batch ${schedule.batchName}" }

                    jobs += launchAtFixedRate(
                        scope = refresherScope,
                        period = schedule.period,
                        initialDelay = schedule.delay,
                        action = {
                            podService.findPod()?.let { pod ->
                                when (schedule.batch) {
                                    is ShardedBatch<*> -> {
                                        try {
                                            logger.debug { "running ${schedule.batchName}" }
                                            schedule.batch.run(pod)
                                        } catch (e: Exception) {
                                            logger.error(e) { "error running batch ${schedule.batchName}" }
                                        }
                                    }

                                    is SinglePodBatch -> {
                                        if (pod.index == schedule.batch.podNumber || !isDockerized) {
                                            try {
                                                logger.debug { "running ${schedule.batchName}" }
                                                schedule.batch.run()
                                            } catch (e: Exception) {
                                                logger.error(e) { "error running batch ${schedule.batchName}" }
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
