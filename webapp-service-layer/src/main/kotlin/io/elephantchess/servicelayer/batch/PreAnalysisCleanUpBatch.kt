package io.elephantchess.servicelayer.batch

import io.elephantchess.model.GameId
import io.elephantchess.servicelayer.batch.definitions.ShardedBatch
import io.elephantchess.servicelayer.services.GameDataService
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PreAnalysisCleanUpBatch(
    private val gameDataService: GameDataService,
    override val logger: KLogger,
) :
    ShardedBatch<Pair<GameId, Instant>>() {

    private val limit = 4.hours

    override fun shardKey(element: Pair<GameId, Instant>) = element.first.id

    override suspend fun fetchAll(): List<Pair<GameId, Instant>> {
        return gameDataService.listPreAnalysisToDelete(limit)
    }

    override suspend fun process(element: Pair<GameId, Instant>) {
        logger.info { "deleting pre-analysis data for ${element.first} started at ${element.second} " }
        gameDataService.resetAnalysis(element.first)
    }

}
