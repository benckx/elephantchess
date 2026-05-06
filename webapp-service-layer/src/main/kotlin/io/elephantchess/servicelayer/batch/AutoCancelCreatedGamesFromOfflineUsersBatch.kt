package io.elephantchess.servicelayer.batch

import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.model.UserType.GUEST
import io.elephantchess.servicelayer.batch.definitions.SimpleKeyShardedBatch
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.days

class AutoCancelCreatedGamesFromOfflineUsersBatch(
    private val pvpGameService: PlayerVsPlayerGameService,
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    override val logger: KLogger,
) :
    SimpleKeyShardedBatch() {

    private val offlineDurationAuthenticated = 7.days
    private val offlineDurationGuest = 2.days

    override suspend fun fetchAll(): List<String> {
        return pvpGameDaoService.listCreatedGamesByOfflineUsers(offlineDurationAuthenticated, AUTHENTICATED) +
                pvpGameDaoService.listCreatedGamesByOfflineUsers(offlineDurationGuest, GUEST)
    }

    override suspend fun process(element: String) {
        logger.debug { "auto-canceling old game $element" }
        pvpGameService.autoCancelGame(gameId = element)
    }

}
