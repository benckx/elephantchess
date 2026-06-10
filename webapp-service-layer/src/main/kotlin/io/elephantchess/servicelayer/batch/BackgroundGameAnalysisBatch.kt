package io.elephantchess.servicelayer.batch

import io.elephantchess.db.services.MoveAnalysisDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.model.UserType.GUEST
import io.elephantchess.servicelayer.batch.definitions.ShardedBatch
import io.elephantchess.servicelayer.services.GameDataService
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.minutes

class BackgroundGameAnalysisBatch(
    private val gameDataService: GameDataService,
    private val moveAnalysisDaoService: MoveAnalysisDaoService,
    private val referenceGameDaoService: ReferenceGameDaoService,
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    private val userDaoService: UserDaoService,
    override val logger: KLogger,
) : ShardedBatch<GameId>() {

    override fun shardKey(element: GameId): String = element.id

    override suspend fun fetchAll(): List<GameId> {
        // Check if there are MAX_CONNECTED_USERS or fewer connected users
        val connectedUsers = userDaoService.countActiveRecently(
            duration = 5.minutes,
            userTypes = listOf(AUTHENTICATED, GUEST)
        )

        if (connectedUsers > MAX_CONNECTED_USERS) {
            logger.debug { "Batch analysis skipped: $connectedUsers connected users (threshold: $MAX_CONNECTED_USERS)" }
            return emptyList()
        }

        // Check if any game is currently being analyzed
        if (moveAnalysisDaoService.isAnyGameCurrentlyBeingAnalyzed()) {
            logger.debug { "Batch analysis skipped: a game is currently being analyzed" }
            return emptyList()
        }

        // Try to pick a random reference game first
        val refGameId = referenceGameDaoService.pickRandomGameForAnalysis()
        if (refGameId != null) {
            return listOf(GameId(GameType.DB, refGameId))
        }

        // If no reference game found, try a PvP game
        val pvpGameId = pvpGameDaoService.pickRandomGameForAnalysis()
        if (pvpGameId != null) {
            return listOf(GameId(GameType.PVP, pvpGameId))
        }

        logger.debug { "Batch analysis skipped: no games available for analysis" }
        return emptyList()
    }

    override suspend fun process(element: GameId) {
        logger.info { "$element starting batch analysis" }
        try {
            gameDataService.startGameAnalysis(element, isFromBatch = true)
            logger.info { "$element successfully started batch analysis" }
        } catch (e: Exception) {
            logger.error { "$element error starting batch analysis: $e" }
        }
    }

    companion object {
        private const val MAX_CONNECTED_USERS = 4
    }

}
