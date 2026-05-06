package io.elephantchess.servicelayer.batch

import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.servicelayer.batch.definitions.SimpleKeyShardedBatch
import io.elephantchess.servicelayer.services.PlayerVsBotGameService
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.days

class AutoResignIdleBotGamesBatch(
    private val pvbGameService: PlayerVsBotGameService,
    private val pvbGameDaoService: PlayerVsBotGameDaoService,
    override val logger: KLogger,
) :
    SimpleKeyShardedBatch() {

    private val idleDuration = 1.days

    override suspend fun fetchAll(): List<String> {
        val ids = pvbGameDaoService.listIdleGames(idleDuration)
        if (ids.isNotEmpty()) {
            logger.info { "found ${ids.size} idle bot game(s) to auto-resign" }
        }
        return ids
    }

    override suspend fun process(element: String) {
        logger.debug { "auto-resigning old game $element" }
        pvbGameService.autoResign(gameId = element, delay = idleDuration)
    }

}
