package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.PuzzleResultDao
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleResult
import io.elephantchess.db.model.PlayedPuzzleRecord
import io.elephantchess.db.model.PuzzleResultCountsRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.PuzzleOutcome
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.DSLContext
import org.jooq.Record5
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class PuzzleResultDaoService(private val dslContext: DSLContext) {

    private val logger = KotlinLogging.logger {}

    suspend fun listPlayedRecently(userId: String, nbrOfDays: Int): List<String> {
        return dslContext
            .selectDistinct(PUZZLE_RESULT.PUZZLE_ID)
            .from(PUZZLE_RESULT)
            .where(PUZZLE_RESULT.USER_ID.eq(userId))
            .and(PUZZLE_RESULT.ENTRY_CREATION.isWithin(nbrOfDays.days))
            .awaitMappedRecords()
    }

    suspend fun listPlayedPuzzles(userId: String, limit: Int, beforeMillis: Long?): List<PlayedPuzzleRecord> {
        var sql = dslContext
            .select(
                PUZZLE_RESULT.PUZZLE_ID,
                PUZZLE.PLAYER_COLOR,
                PUZZLE.START_FEN,
                PUZZLE_RESULT.OUTCOME,
                PUZZLE_RESULT.PLAYER_RATING_FROM,
                PUZZLE_RESULT.PLAYER_RATING_TO,
                PUZZLE_RESULT.PUZZLE_RATING_FROM,
                PUZZLE_RESULT.PUZZLE_RATING_TO,
                PUZZLE_RESULT.ENTRY_CREATION
            )
            .from(PUZZLE_RESULT, PUZZLE)
            .where(PUZZLE_RESULT.USER_ID.eq(userId))
            .and(PUZZLE_RESULT.PUZZLE_ID.eq(PUZZLE.ID))

        if (beforeMillis != null) {
            sql = sql.and(PUZZLE_RESULT.ENTRY_CREATION.isBeforeEpochMillis(beforeMillis))
        }

        return sql
            .orderBy(PUZZLE_RESULT.ENTRY_CREATION.desc())
            .limit(limit)
            .awaitRecords()
            .map { record ->
                PlayedPuzzleRecord(
                    puzzleId = record.get(PUZZLE_RESULT.PUZZLE_ID),
                    color = record.get(PUZZLE.PLAYER_COLOR),
                    startFen = record.get(PUZZLE.START_FEN),
                    outcome = record.get(PUZZLE_RESULT.OUTCOME),
                    ratingFrom = record.get(PUZZLE_RESULT.PLAYER_RATING_FROM),
                    ratingTo = record.get(PUZZLE_RESULT.PLAYER_RATING_TO),
                    puzzleRatingFrom = record.get(PUZZLE_RESULT.PUZZLE_RATING_FROM),
                    puzzleRatingTo = record.get(PUZZLE_RESULT.PUZZLE_RATING_TO),
                    date = record.get(PUZZLE_RESULT.ENTRY_CREATION),
                )
            }
    }

    suspend fun latestPlayedPuzzle(): Instant? {
        return dslContext
            .select(PUZZLE_RESULT.ENTRY_CREATION)
            .from(PUZZLE_RESULT)
            .orderBy(PUZZLE_RESULT.ENTRY_CREATION.desc())
            .limit(1)
            .awaitSingleValue()
    }

suspend fun fetchPuzzleResultCounts(userIds: List<String>): List<PuzzleResultCountsRecord> {
    if (userIds.isEmpty()) return emptyList()

    val solvedField = DSL.count().filterWhere(PUZZLE_RESULT.OUTCOME.eq(PuzzleOutcome.SOLVED)).`as`("nbr_solved")
    val failedField = DSL.count().filterWhere(PUZZLE_RESULT.OUTCOME.eq(PuzzleOutcome.FAILED)).`as`("nbr_failed")
    val totalField = DSL.count().`as`("nbr_total")

    return dslContext
        .select(PUZZLE_RESULT.USER_ID, solvedField, failedField, totalField)
        .from(PUZZLE_RESULT)
        .where(PUZZLE_RESULT.USER_ID.`in`(userIds))
        .groupBy(PUZZLE_RESULT.USER_ID)
        .awaitRecords()
        .map { record ->
            PuzzleResultCountsRecord(
                userId = requireNotNull(record.value1()),
                solved = record.value2(),
                failed = record.value3(),
                total = record.value4()
            )
        }
}

    suspend fun latestPuzzleVote(): Instant? {
        return dslContext
            .select(PUZZLE_RESULT.ENTRY_CREATION)
            .from(PUZZLE_RESULT)
            .where(PUZZLE_RESULT.UP_VOTED.isNotNull)
            .orderBy(PUZZLE_RESULT.ENTRY_CREATION.desc())
            .limit(1)
            .awaitSingleValue()
    }

    suspend fun persistOutcome(
        userId: String,
        puzzleId: String,
        outcome: PuzzleOutcome,
        usedPreRecordedSolution: Boolean,
        rePlayabilityTimeLimit: Duration,
        eloTransfer: (PuzzleOutcome, Int, Int, Instant?) -> Pair<Int, Int>,
    ): Pair<Int?, Int?> {
        var playerOldRating: Int? = null
        var playerNewRating: Int? = null

        dslContext.transactionCoroutine { cfg ->
            val transaction = DSL.using(cfg)
            val puzzleRating = fetchPuzzleRating(puzzleId, transaction)
                ?: throw IllegalArgumentException("Puzzle $puzzleId not found")
            val userRating = fetchUserPuzzleRating(userId, transaction)
                ?: throw IllegalArgumentException("User $userId not found")

            val (newUserRating, newPuzzleRating) =
                eloTransfer(
                    outcome,
                    userRating,
                    puzzleRating,
                    fetchLastPlayed(userId, puzzleId, rePlayabilityTimeLimit, transaction)
                )

            val puzzleResult = PuzzleResult()
            puzzleResult.userId = userId
            puzzleResult.puzzleId = puzzleId
            puzzleResult.playerRatingFrom = userRating
            puzzleResult.puzzleRatingFrom = puzzleRating
            puzzleResult.playerRatingTo = newUserRating
            puzzleResult.puzzleRatingTo = newPuzzleRating
            puzzleResult.outcome = outcome
            puzzleResult.usedPreRecordedSolution = usedPreRecordedSolution

            updateUserRating(userId, puzzleResult.playerRatingTo, transaction)
            updatePuzzleRating(puzzleId, puzzleResult.puzzleRatingTo, transaction)

            val playerDelta = puzzleResult.playerRatingTo - puzzleResult.playerRatingFrom
            val puzzleDelta = puzzleResult.puzzleRatingTo - puzzleResult.puzzleRatingFrom

            playerOldRating = puzzleResult.playerRatingFrom
            playerNewRating = puzzleResult.playerRatingTo

            logger.debug {
                "[$userId] user ${puzzleResult.playerRatingFrom} -> ${puzzleResult.playerRatingTo}" +
                        " (${renderDelta(playerDelta)})"
            }
            logger.debug {
                "[$puzzleId] puzzle ${puzzleResult.puzzleRatingFrom} -> ${puzzleResult.puzzleRatingTo}" +
                        " (${renderDelta(puzzleDelta)})"
            }

            PuzzleResultDao(cfg).insertReactive(puzzleResult)
        }

        return Pair(playerOldRating, playerNewRating)
    }

    /**
     * @return null if record wasn't found, or the id of the record otherwise
     */
    suspend fun persistVote(puzzleId: String, userId: String, upVoted: Boolean, delay: Duration): Int? {
        return dslContext.transactionCoroutine { cfg ->
            val transactional = DSL.using(cfg)

            val id = transactional
                .select(PUZZLE_RESULT.ID)
                .from(PUZZLE_RESULT)
                .where(PUZZLE_RESULT.USER_ID.eq(userId))
                .and(PUZZLE_RESULT.PUZZLE_ID.eq(puzzleId))
                .and(PUZZLE_RESULT.ENTRY_CREATION.isWithin(delay))
                .orderBy(PUZZLE_RESULT.ENTRY_CREATION.desc())
                .limit(1)
                .awaitSingleValue<Int>()

            if (id != null) {
                transactional
                    .update(PUZZLE_RESULT)
                    .set(PUZZLE_RESULT.UP_VOTED.fixed(), upVoted)
                    .where(PUZZLE_RESULT.ID.fixed().eq(id))
                    .awaitExecute()
            }

            id
        }
    }

    private suspend fun fetchPuzzleRating(puzzleId: String, context: DSLContext): Int? {
        return context
            .select(PUZZLE.RATING)
            .from(PUZZLE)
            .where(PUZZLE.ID.eq(puzzleId))
            .awaitSingleValue()
    }

    private suspend fun fetchUserPuzzleRating(userId: String, context: DSLContext): Int? {
        return context
            .select(USER.PUZZLE_RATING)
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleValue()
    }

    private suspend fun updatePuzzleRating(puzzleId: String, rating: Int, context: DSLContext) {
        context
            .update(PUZZLE.fixed())
            .set(PUZZLE.RATING.fixed(), rating)
            .where(PUZZLE.ID.eq(puzzleId))
            .awaitExecute()
    }

    private suspend fun updateUserRating(userId: String, rating: Int, context: DSLContext) {
        context
            .update(USER.fixed())
            .set(USER.PUZZLE_RATING.fixed(), rating)
            .where(USER.ID.eq(userId))
            .awaitExecute()
    }

    private suspend fun fetchLastPlayed(
        userId: String,
        puzzleId: String,
        duration: Duration,
        context: DSLContext,
    ): Instant? {
        return context
            .select(PUZZLE_RESULT.ENTRY_CREATION)
            .from(PUZZLE_RESULT)
            .where(PUZZLE_RESULT.USER_ID.eq(userId))
            .and(PUZZLE_RESULT.PUZZLE_ID.eq(puzzleId))
            .and(PUZZLE_RESULT.ENTRY_CREATION.isWithin(duration))
            .orderBy(PUZZLE_RESULT.ENTRY_CREATION.desc())
            .limit(1)
            .awaitSingleValue()
    }

    /**
     * Always includes SKIPPED result.
     */
    suspend fun countAllAttempts(): Map<String, Int> {
        val mapLoggedInUsers = dslContext
            .select(PUZZLE_RESULT.PUZZLE_ID, DSL.count())
            .from(PUZZLE_RESULT)
            .groupBy(PUZZLE_RESULT.PUZZLE_ID)
            .awaitRecords()
            .associate { it.get(PUZZLE_RESULT.PUZZLE_ID) to it.get(DSL.count()) }

        val mapAnonymous = dslContext
            .select(PUZZLE_RESULT_ANONYMOUS.PUZZLE_ID, DSL.count())
            .from(PUZZLE_RESULT_ANONYMOUS)
            .groupBy(PUZZLE_RESULT_ANONYMOUS.PUZZLE_ID)
            .awaitRecords()
            .associate { it.get(PUZZLE_RESULT_ANONYMOUS.PUZZLE_ID) to it.get(DSL.count()) }

        return (mapLoggedInUsers.keys + mapAnonymous.keys).associateWith { puzzleId ->
            val countLoggedInUsers = mapLoggedInUsers[puzzleId] ?: 0
            val countAnonymous = mapAnonymous[puzzleId] ?: 0
            countLoggedInUsers + countAnonymous
        }
    }

    suspend fun listLastPuzzlePlayedByLoggedUsers(n: Int): List<Record5<String, PuzzleOutcome, String, Instant, Boolean>> {
        return dslContext
            .select(
                PUZZLE_RESULT.PUZZLE_ID,
                PUZZLE_RESULT.OUTCOME,
                USER.ID,
                PUZZLE_RESULT.ENTRY_CREATION,
                PUZZLE_RESULT.UP_VOTED
            )
            .from(PUZZLE_RESULT, USER)
            .where(PUZZLE_RESULT.USER_ID.eq(USER.ID))
            .orderBy(PUZZLE_RESULT.ENTRY_CREATION.desc())
            .limit(n)
            .awaitRecords()
    }

    private companion object {

        fun renderDelta(value: Int): String {
            return if (value > 0) {
                "+$value"
            } else {
                value.toString()
            }
        }

    }

    suspend fun transferFromGuestToUser(guestUserId: String, newUserId: String) {
        dslContext
            .update(PUZZLE_RESULT)
            .set(PUZZLE_RESULT.USER_ID, newUserId)
            .set(PUZZLE_RESULT.GUEST_USER_ID, guestUserId)
            .where(PUZZLE_RESULT.USER_ID.eq(guestUserId))
            .awaitExecute()
    }

}
