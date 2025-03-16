package io.elephantchess.engines.process

data class EngineConfig(
    val version: String? = null,
    // number of engines processes to keep in the pool
    val poolSize: Int = 1,
    // threads per engine process
    val numberOfThreads: Int = 1,
)
