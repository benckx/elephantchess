package io.elephantchess.servicelayer.batch.definitions

interface SinglePodBatch : Batch {

    val podNumber: Int
    suspend fun run()

}
