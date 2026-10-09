package io.elephantchess.servicelayer.batch.definitions

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

open class BatchSchedule<T : Batch>(
    val batch: T,
    val period: Duration,
    val delay: Duration = 5.minutes,
) {

    val batchName: String
        get() = batch.javaClass.simpleName

    override fun toString() = "BatchSchedule(${batchName}, period=$period, delay=$delay)"

}

/**
 * A [BatchSchedule] for a [SinglePodBatch], carrying the [podNumber] the batch should run on so pod
 * assignment is visible at the schedule site rather than hidden inside the batch implementation.
 */
class SinglePodBatchSchedule<T : SinglePodBatch>(
    batch: T,
    period: Duration,
    delay: Duration = 5.minutes,
    val podNumber: Int,
) : BatchSchedule<T>(batch, period, delay)
