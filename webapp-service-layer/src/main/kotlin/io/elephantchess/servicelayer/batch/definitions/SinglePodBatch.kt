package io.elephantchess.servicelayer.batch.definitions

interface SinglePodBatch : Batch {

    suspend fun run()

}
