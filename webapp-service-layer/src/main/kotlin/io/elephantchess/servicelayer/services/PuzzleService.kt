package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.model.PuzzleRecord
import io.elephantchess.db.services.PuzzleDaoService
import io.elephantchess.db.services.PuzzleResultDaoService
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.utils.toUtcInstant
import io.elephantchess.engines.EnginePool
import io.elephantchess.model.Engine.PIKAFISH
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType
import io.elephantchess.model.PuzzleCategory
import io.elephantchess.model.PuzzleOutcome
import io.elephantchess.servicelayer.dto.gamedata.GameMetadataDto
import io.elephantchess.servicelayer.dto.puzzles.*
import io.elephantchess.servicelayer.exceptions.InternalErrorException
import io.elephantchess.servicelayer.exceptions.NotAcceptableException
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.utils.modelToProcess
import io.elephantchess.utils.EloCalculator.calculateElo
import io.github.oshai.kotlinlogging.KLogger
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PuzzleService(
    private val enginesPool: EnginePool,
    private val puzzleDaoService: PuzzleDaoService,
    private val puzzleResultDaoService: PuzzleResultDaoService,
    private val referenceGameDaoService: ReferenceGameDaoService,
    private val puzzleCache: PuzzleCache,
    private val logger: KLogger,
) {

    suspend fun bestMove(request: PuzzleBestMoveRequest): PuzzleBestMoveResponse {
        val result = enginesPool.queryForDepth(
            fen = request.fen,
            engineId = modelToProcess(PIKAFISH),
            depth = 10,
            timeout = 15_000
        )

        result?.bestMove?.let { return PuzzleBestMoveResponse(it) }
        throw InternalErrorException("No response from engine")
    }

    suspend fun fetchById(puzzleId: String, categories: List<PuzzleCategory>): PuzzleResponse {
        return if (puzzleCache.exists(puzzleId) && puzzleCache.hasCategories(puzzleId, categories)) {
            fetchAndMapToDto(puzzleId)
        } else {
            throw NotFoundException("Puzzle $puzzleId not found")
        }
    }

    suspend fun fetchCurrent(userId: String, categories: List<PuzzleCategory>): PuzzleResponse {
        val currentlyAssignedPuzzleId = puzzleDaoService.findCurrentlyAssignedTo(userId)
        return if (
            currentlyAssignedPuzzleId != null &&
            puzzleCache.hasCategories(currentlyAssignedPuzzleId, categories)
        ) {
            logger.debug { "loading last puzzle assigned to $userId -> $currentlyAssignedPuzzleId" }
            fetchAndMapToDto(currentlyAssignedPuzzleId)
        } else {
            logger.debug { "no puzzle assigned to $userId, fetching the next" }
            fetchNextPuzzleForUser(userId, categories)
        }
    }

    suspend fun fetchNextPuzzleForUser(userId: String, categories: List<PuzzleCategory>): PuzzleResponse {
        try {
            val playedRecently = puzzleResultDaoService.listPlayedRecently(userId, MIN_DAYS_BETWEEN_PUZZLES)
            val userRating = puzzleDaoService.findPuzzleRatingOfUser(userId)
            val puzzleId =
                puzzleCache.randomId(
                    userRating = userRating,
                    range = ELO_RANGE,
                    exclude = playedRecently,
                    categories = categories
                )

            logger.debug { "assigning new puzzle to $userId -> $puzzleId" }
            puzzleDaoService.assign(userId, puzzleId)
            return fetchAndMapToDto(puzzleId)
        } catch (e: NoSuchElementException) {
            logger.warn { "puzzle cache not ready yet: $e" }
            throw NotAcceptableException("Puzzle cache not ready yet")
        }
    }

    suspend fun listPlayedPuzzles(userId: String, beforeTs: Long?): PlayedPuzzlesResponse {
        return puzzleResultDaoService
            .listPlayedPuzzles(userId, 30, beforeTs)
            .map { record ->
                PlayedPuzzlesResponse.Entry(
                    puzzleId = record.puzzleId,
                    playerColor = record.color,
                    startFen = record.startFen,
                    categories = puzzleCache.listCategories(record.puzzleId),
                    outcome = record.outcome,
                    ratingFrom = record.ratingFrom,
                    ratingTo = record.ratingTo,
                    date = record.date.toEpochMilliseconds(),
                )
            }
            .let { entries ->
                PlayedPuzzlesResponse(entries)
            }
    }

    suspend fun fetchPuzzlesOriginalGameMetadata(request: PuzzlesOriginalGameMetadataRequest): PuzzlesOriginalGameMetadataResponse {
        val records = referenceGameDaoService.findByPuzzleIds(request.puzzleIds)
        val entries = request.puzzleIds.mapNotNull { puzzleId ->
            val puzzleRecords = records.filter { record -> record.get(PUZZLE.ID) == puzzleId }
            val referenceGameId = puzzleRecords.map { record -> record.get(REFERENCE_GAME.ID) }.firstOrNull()
            val redPlayerId = puzzleRecords.map { record -> record.get(REFERENCE_GAME.RED_PLAYER) }.firstOrNull()
            val blackPlayerId = puzzleRecords.map { record -> record.get(REFERENCE_GAME.BLACK_PLAYER) }.firstOrNull()
            val outcome = puzzleRecords.map { record -> record.get(REFERENCE_GAME.OUTCOME) }.firstOrNull()
            val date = puzzleRecords.map { record -> record.get(REFERENCE_GAME.DATE) }.firstOrNull()

            val redPlayerName = puzzleRecords
                .filter { redPlayerId != null && it.get(REFERENCE_PLAYER.ID) == redPlayerId }
                .map { record -> record.get(REFERENCE_PLAYER.CANONICAL_NAME) }
                .firstOrNull()

            val blackPlayerName = puzzleRecords
                .filter { blackPlayerId != null && it.get(REFERENCE_PLAYER.ID) == blackPlayerId }
                .map { record -> record.get(REFERENCE_PLAYER.CANONICAL_NAME) }
                .firstOrNull()

            if (referenceGameId != null) {
                val metadata = GameMetadataDto(
                    gameId = GameId(GameType.DB, referenceGameId),
                    redPlayerId = redPlayerId,
                    redPlayerName = redPlayerName,
                    blackPlayerId = blackPlayerId,
                    blackPlayerName = blackPlayerName,
                    finalFen = puzzleRecords.map { record -> record.get(REFERENCE_GAME.FINAL_FEN) }.first(),
                    analysisStatus = puzzleRecords.map { record -> record.get(REFERENCE_GAME.ANALYSIS_STATUS) }.first(),
                    outcome = outcome,
                    lastUpdated = date?.atStartOfDay()?.toUtcInstant()?.toEpochMilliseconds(),
                )
                PuzzlesOriginalGameMetadataResponse.Entry(puzzleId, metadata)
            } else {
                null
            }
        }

        return PuzzlesOriginalGameMetadataResponse(entries)
    }

    suspend fun processOutcome(request: PuzzleOutcomeRequest, userId: String): PuzzleOutcomeResponse {
        puzzleDaoService.unAssign(userId)
        return mapOutcomeToDto(
            puzzleResultDaoService.persistOutcome(
                userId,
                request.puzzleId,
                request.outcome,
                request.usedPreRecordedSolution,
                RE_PLAYABILITY_DAYS.days,
                eloTransfer(request.visibleCategories)
            )
        )
    }

    suspend fun persistVote(request: PuzzleVoteRequest, userId: String): PuzzleVoteResponse {
        val resultId =
            puzzleResultDaoService.persistVote(
                puzzleId = request.puzzleId,
                userId = userId,
                upVoted = request.upVoted,
                delay = 10.minutes
            )

        return PuzzleVoteResponse(resultId != null)
    }

    private fun eloTransfer(visibleCategories: Boolean): (PuzzleOutcome, Int, Int, Instant?) -> Pair<Int, Int> {
        return { outcome, userRating, puzzleRating, lastPlayed ->
            val newUserRating: Int
            val newPuzzleRating: Int
            val defaultK = 16
            var kSolved = defaultK

            if (lastPlayed != null) {
                val now = Clock.System.now()
                val daysSinceLastPlayed = (now - lastPlayed).inWholeDays
                if (daysSinceLastPlayed < RE_PLAYABILITY_DAYS) {
                    kSolved = ceil((defaultK / RE_PLAYABILITY_DAYS.toDouble()) * daysSinceLastPlayed).toInt()
                    if (logger.isDebugEnabled() && kSolved < defaultK) {
                        logger.debug { "kSolved limited to $kSolved because the puzzle was played $daysSinceLastPlayed day(s) ago" }
                    }
                }
            }

            if (visibleCategories) {
                kSolved /= 2
            }

            when (outcome) {
                PuzzleOutcome.SOLVED -> {
                    val eloTransfer = calculateElo(userRating, puzzleRating, kSolved)
                    newUserRating = eloTransfer.winnerNewRating
                    newPuzzleRating = eloTransfer.loserNewRating
                }

                PuzzleOutcome.FAILED, PuzzleOutcome.SKIPPED -> {
                    val eloTransfer = calculateElo(puzzleRating, userRating, defaultK)
                    newUserRating = eloTransfer.loserNewRating
                    newPuzzleRating = eloTransfer.winnerNewRating
                }
            }

            Pair(newUserRating, newPuzzleRating)
        }
    }

    suspend fun fetchPuzzleRating(userId: String): PuzzleRatingResponse {
        val rating = puzzleDaoService.fetchRatingForUser(userId)
        if (rating == null) {
            throw NotFoundException("User $userId could not be found")
        } else {
            return PuzzleRatingResponse(rating)
        }
    }

    private suspend fun fetchAndMapToDto(puzzleId: String): PuzzleResponse {
        fun mapRecordToDto(record: PuzzleRecord): PuzzleResponse {
            val attempts = puzzleCache.getById(puzzleId)?.attempts ?: 0

            return PuzzleResponse(
                id = record.puzzle.id,
                fen = record.puzzle.startFen,
                color = record.puzzle.playerColor,
                attempts = attempts,
                rating = record.puzzle.rating,
                categories = record.categories.mapNotNull { it.category },
                moves = record.moves.filterNot { move -> move.isSolution }.map { move -> move.uci },
                solution = record.moves.filter { move -> move.isSolution }.map { move -> move.uci },
            )
        }

        return mapRecordToDto(puzzleDaoService.fetchById(puzzleId))
    }

    companion object {

        const val ELO_RANGE = 800
        const val MIN_DAYS_BETWEEN_PUZZLES = 60
        const val RE_PLAYABILITY_DAYS = 90

        fun mapOutcomeToDto(outcome: Pair<Int?, Int?>): PuzzleOutcomeResponse {
            return PuzzleOutcomeResponse(outcome.first, outcome.second)
        }

    }

}
