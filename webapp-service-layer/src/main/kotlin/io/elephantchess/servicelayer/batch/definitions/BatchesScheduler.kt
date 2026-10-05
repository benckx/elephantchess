package io.elephantchess.servicelayer.batch.definitions

import io.elephantchess.utils.di.KoinSingleton

import io.elephantchess.config.AppConfig
import io.elephantchess.servicelayer.metrics.MetricsLogger
import io.elephantchess.servicelayer.services.ExceptionService
import io.elephantchess.servicelayer.services.PodService
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRate
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@KoinSingleton(eager = true)
class BatchesScheduler(
    appConfig: AppConfig,
    schedules: List<BatchSchedule<out Batch>>,
    private val podService: PodService,
    private val exceptionService: ExceptionService,
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
            .filterIsInstance<SinglePodBatchSchedule<*>>()
            .forEach { schedule ->
                logger.info { "${schedule.batchName} will be scheduled on pod ${schedule.podNumber}" }
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
                                when (val batch = schedule.batch) {
                                    is ShardedBatch<*> -> {
                                        try {
                                            logger.debug { "running ${schedule.batchName}" }
                                            batch.run(pod)
                                        } catch (e: Exception) {
                                            logger.error(e) { "error running batch ${schedule.batchName}" }
                                            exceptionService.saveException(e)
                                        }
                                    }

                                    is SinglePodBatch -> {
                                        val podNumber = (schedule as? SinglePodBatchSchedule<*>)?.podNumber ?: 0
                                        if (pod.index == podNumber || !isDockerized) {
                                            try {
                                                logger.debug { "running ${schedule.batchName}" }
                                                batch.run()
                                            } catch (e: Exception) {
                                                logger.error(e) { "error running batch ${schedule.batchName}" }
                                                exceptionService.saveException(e)
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
