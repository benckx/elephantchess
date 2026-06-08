package io.elephantchess.servicelayer.batch

import io.elephantchess.db.services.MoveAnalysisDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.servicelayer.services.GameDataService
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.minutes

class PassiveGameAnalysisBatch(
    private val gameDataService: GameDataService,
    private val moveAnalysisDaoService: MoveAnalysisDaoService,
    private val referenceGameDaoService: ReferenceGameDaoService,
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    private val userDaoService: UserDaoService,
    override val logger: KLogger,
) : SinglePodBatch {

    override val podNumber: Int = 0

    override suspend fun run() {
        // Check if there are 4 or fewer connected users
        val connectedUsers = userDaoService.countActiveRecently(
            duration = 5.minutes,
            userTypes = listOf(io.elephantchess.model.UserType.AUTHENTICATED, io.elephantchess.model.UserType.GUEST)
        )

        if (connectedUsers > 4) {
            logger.debug { "Passive analysis skipped: $connectedUsers connected users (threshold: 4)" }
            return
        }

        // Check if any game is currently being analyzed
        if (moveAnalysisDaoService.isAnyGameCurrentlyBeingAnalyzed()) {
            logger.debug { "Passive analysis skipped: a game is currently being analyzed" }
            return
        }

        // Try to pick a random reference game first
        val refGameId = referenceGameDaoService.pickRandomGameForAnalysis()
        if (refGameId != null) {
            logger.info { "Starting passive analysis for reference game: $refGameId" }
            val gameId = GameId(GameType.DB, refGameId)
            try {
                gameDataService.startGameAnalysis(gameId, isPassive = true)
                logger.info { "Successfully started passive analysis for reference game: $refGameId" }
            } catch (e: Exception) {
                logger.error(e) { "Error starting passive analysis for reference game: $refGameId" }
            }
            return
        }

        // If no reference game found, try a PvP game
        val pvpGameId = pvpGameDaoService.pickRandomGameForAnalysis()
        if (pvpGameId != null) {
            logger.info { "Starting passive analysis for PvP game: $pvpGameId" }
            val gameId = GameId(GameType.PVP, pvpGameId)
            try {
                gameDataService.startGameAnalysis(gameId, isPassive = true)
                logger.info { "Successfully started passive analysis for PvP game: $pvpGameId" }
            } catch (e: Exception) {
                logger.error(e) { "Error starting passive analysis for PvP game: $pvpGameId" }
            }
            return
        }

        logger.debug { "Passive analysis skipped: no games available for analysis" }
    }

}
