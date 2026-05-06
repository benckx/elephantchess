package io.elephantchess.servicelayer.batch.definitions

abstract class SimpleKeyShardedBatch : ShardedBatch<String>() {

    override fun shardKey(element: String): String = element

}
