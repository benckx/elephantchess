package io.elephantchess.servicelayer.batch.definitions

import io.elephantchess.servicelayer.model.Pod

abstract class ShardedBatch<T> : Batch {

    abstract fun shardKey(element: T): String

    abstract suspend fun fetchAll(): List<T>

    abstract suspend fun process(element: T)

    suspend fun run(pod: Pod) {
        val allElements = fetchAll()
        val elementsForPod = allElements.filter { element -> pod.mustProcess(shardKey(element)) }

        if (elementsForPod.isNotEmpty()) {
            logger.debug { "processing ${elementsForPod.size}/${allElements.size} elements on $pod" }
            elementsForPod.forEach { element ->
                try {
                    process(element)
                } catch (e: Exception) {
                    logger.warn(e) { "error processing $element" }
                }
            }
        }
    }

}
