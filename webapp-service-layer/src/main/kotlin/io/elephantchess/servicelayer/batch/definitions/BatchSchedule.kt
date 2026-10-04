package io.elephantchess.servicelayer.batch.definitions

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class BatchSchedule<T : Batch>(
    val batch: T,
    val period: Duration,
    val delay: Duration = 5.minutes,
) {

    val batchName: String
        get() = batch.javaClass.simpleName

    override fun toString() = "BatchSchedule(${batchName}, period=$period, delay=$delay)"

}
