package io.elephantchess.servicelayer.batch.definitions

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class BatchSchedule<T : Batch>(
    val batch: T,
    val period: Duration,
    val delay: Duration = 5.minutes,
) {

    val batchName: String
        get() = batch.javaClass.simpleName

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
) : BatchSchedule<T>(batch, period, delay) {

    override fun toString() = "SinglePodBatchSchedule(${batchName}, period=$period, delay=$delay, podNumber=$podNumber)"

}

class ShardedBatchSchedule<B : ShardedBatch<*>>(
    batch: B,
    period: Duration,
    delay: Duration = 5.minutes,
) : BatchSchedule<B>(batch, period, delay) {

    override fun toString(): String = "ShardedBatchSchedule(${batchName}, period=$period, delay=$delay)"

}
