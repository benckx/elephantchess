package io.elephantchess.db.services

import io.elephantchess.db.callback.BotMove
import io.elephantchess.db.callback.PlayMoveBotGameCallbackResult
import io.elephantchess.db.dao.codegen.Tables.BOT_GAME_MOVE
import io.elephantchess.db.dao.codegen.tables.BotGame.BOT_GAME
import io.elephantchess.db.dao.codegen.tables.daos.BotGameDao
import io.elephantchess.db.dao.codegen.tables.daos.BotGameMoveDao
import io.elephantchess.db.dao.codegen.tables.daos.BotGameStatusEventDao
import io.elephantchess.db.dao.codegen.tables.pojos.BotGame
import io.elephantchess.db.dao.codegen.tables.pojos.BotGameMove
import io.elephantchess.db.dao.codegen.tables.pojos.BotGameStatusEvent
import io.elephantchess.db.model.BotGameStatusRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.AnalysisStatus.CANCELLED
import io.elephantchess.model.AnalysisStatus.STARTED
import io.elephantchess.model.BotGameMoveType
import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameEventType.*
import io.elephantchess.model.Outcome
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class PlayerVsBotGameDaoService(private val dslContext: DSLContext) {

    private val logger = KotlinLogging.logger {}

    suspend fun insertGame(gameRecord: BotGame, statusRecord: BotGameStatusEvent) {
        dslContext.transactionCoroutine { configuration ->
            BotGameDao(configuration).insertReactive(gameRecord)
            BotGameStatusEventDao(configuration).insertReactive(statusRecord)
        }
    }

    suspend fun listGamesByUserId(userId: String, limit: Int, beforeTs: Long?): List<BotGame> {
        var sql = dslContext
            .select()
            .from(BOT_GAME)
            .where(BOT_GAME.USER_ID.eq(userId))

        if (beforeTs != null) {
            sql = sql.and(BOT_GAME.LAST_UPDATED.isBeforeEpochMillis(beforeTs))
        }

        return sql
            .orderBy(BOT_GAME.LAST_UPDATED.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

    suspend fun listIdleGames(duration: Duration): List<String> {
        return dslContext
            .select(BOT_GAME.ID)
            .from(BOT_GAME)
            .where(BOT_GAME.LAST_UPDATED.isOlderThan(duration))
            .and(BOT_GAME.OUTCOME.isNull)
            .and(BOT_GAME.GAME_STATUS.eq(CREATED))
            .awaitMappedRecords()
    }

    suspend fun listLatestGamesByIdentifiedUsers(
        limit: Int,
        minMoveIndex: Int? = null,
        beforeTs: Long? = null,
        excludeAutoResigned: Boolean = false,
        distinctByUsers: Boolean = false
    ): List<BotGame> {
        val conditions = mutableListOf<Condition>()
        conditions += BOT_GAME.USER_ID.isNotNull

        if (minMoveIndex != null) {
            conditions += BOT_GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex)
        }

        if (beforeTs != null) {
            conditions += BOT_GAME.LAST_UPDATED.isBeforeEpochMillis(beforeTs)
        }

        if (excludeAutoResigned) {
            conditions += BOT_GAME.GAME_STATUS.ne(AUTO_RESIGNED)
        }

        return if (distinctByUsers) {
            val rn = DSL.rowNumber()
                .over(DSL.partitionBy(BOT_GAME.USER_ID).orderBy(BOT_GAME.LAST_UPDATED.desc()))
                .`as`("rn")

            val sub = dslContext
                .select(BOT_GAME.asterisk(), rn)
                .from(BOT_GAME)
                .where(conditions)
                .asTable("t")

            dslContext
                .select(sub.asterisk())
                .from(sub)
                .where(sub.field("rn", Int::class.java)!!.eq(1))
                .orderBy(sub.field(BOT_GAME.LAST_UPDATED)!!.desc())
                .limit(limit)
                .awaitMappedRecords()
        } else {
            dslContext
                .select()
                .from(BOT_GAME)
                .where(conditions)
                .orderBy(BOT_GAME.LAST_UPDATED.desc())
                .limit(limit)
                .awaitMappedRecords()
        }
    }

    suspend fun listPreAnalysisToDelete(limit: Duration): List<Pair<String, Instant>> {
        return dslContext
            .select(BOT_GAME.ID, BOT_GAME.ANALYSIS_START_TIME)
            .from(BOT_GAME)
            .where(BOT_GAME.ANALYSIS_STATUS.`in`(STARTED, CANCELLED))
            .and(BOT_GAME.ANALYSIS_START_TIME.isOlderThan(limit))
            .awaitRecords()
            .map { record2 ->
                Pair(
                    record2.get(BOT_GAME.ID),
                    record2.get(BOT_GAME.ANALYSIS_START_TIME)
                )
            }
    }

    suspend fun latestGameActivity(minMoveIndex: Int = 0): Instant? {
        return dslContext
            .select(DSL.max(BOT_GAME.LAST_UPDATED))
            .from(BOT_GAME)
            .where(BOT_GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex))
            .and(BOT_GAME.GAME_STATUS.notIn(listOf(FLAGGED, AUTO_CANCELED)))
            .awaitSingleValue()
    }

    suspend fun latestMoveTime(minMoveIndex: Int = 0): Instant? {
        return dslContext
            .select(DSL.max(BOT_GAME.LAST_UPDATED))
            .from(BOT_GAME)
            .where(BOT_GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex))
            .awaitSingleValue()
    }

    suspend fun countLiveGames(duration: Duration): Int {
        return dslContext
            .selectCount()
            .from(BOT_GAME)
            .where(BOT_GAME.GAME_STATUS.`in`(CREATED))
            .and(BOT_GAME.LAST_UPDATED.isWithin(duration))
            .awaitSingleValue()!!
    }

    suspend fun fetchById(gameId: String): BotGame? {
        return dslContext
            .select()
            .from(BOT_GAME)
            .where(BOT_GAME.ID.eq(gameId))
            .awaitSingleMappedRecord()
    }

    suspend fun fetchStartFen(gameId: String): String? {
        return dslContext
            .select(BOT_GAME.START_FEN)
            .from(BOT_GAME)
            .where(BOT_GAME.ID.eq(gameId))
            .awaitSingleValue()
    }

    suspend fun fetchGameStatus(gameId: String): BotGameStatusRecord? {
        return dslContext
            .select(
                BOT_GAME.USER_ID,
                BOT_GAME.USER_COLOR,
                BOT_GAME.GAME_STATUS,
                BOT_GAME.CURRENT_HALF_MOVE_INDEX
            )
            .from(BOT_GAME)
            .where(BOT_GAME.ID.eq(gameId))
            .awaitMappedRecords<BotGame>()
            .firstOrNull()
            ?.let { record ->
                BotGameStatusRecord(
                    record.userId,
                    record.userColor,
                    record.gameStatus,
                    record.currentHalfMoveIndex
                )
            }
    }

    suspend fun fetchCurrentStatusOfGames(gameIds: List<String>): Map<String, Pair<GameEventType, Outcome?>> {
        return if (gameIds.isEmpty()) {
            emptyMap()
        } else {
            dslContext
                .select(
                    BOT_GAME.ID,
                    BOT_GAME.GAME_STATUS,
                    BOT_GAME.OUTCOME
                )
                .from(BOT_GAME)
                .where(BOT_GAME.ID.`in`(gameIds))
                .awaitMappedRecords<BotGame>()
                .associate { record -> record.id to (record.gameStatus to record.outcome) }
                .toMap()
        }
    }

    suspend fun fetchCurrentStatusAndFen(gameIds: List<String>): List<BotGame> {
        return if (gameIds.isEmpty()) {
            emptyList()
        } else {
            dslContext
                .select(
                    BOT_GAME.ID,
                    BOT_GAME.GAME_STATUS,
                    BOT_GAME.CURRENT_FEN,
                    BOT_GAME.LAST_UPDATED
                )
                .from(BOT_GAME)
                .where(BOT_GAME.ID.`in`(gameIds))
                .awaitMappedRecords<BotGame>()
        }
    }

    /**
     * @param tuples list of gameId and half move index to request from
     * @return list of moves for each gameId, position and uci
     */
    suspend fun fetchNewMovesForGamesAndIndexes(tuples: List<Pair<String, Int>>): List<BotGameMove> {
        if (tuples.isEmpty()) {
            return emptyList()
        }

        fun conditionForPair(gameId: String, moveIndex: Int) =
            BOT_GAME_MOVE.BOT_GAME_ID.eq(gameId).and(BOT_GAME_MOVE.POSITION.ge(moveIndex))

        val conditionForPairs =
            tuples
                .map { (gameId, moveIndex) -> conditionForPair(gameId, moveIndex) }
                .reduce { acc, condition -> acc.or(condition) }

        return dslContext
            .select(
                BOT_GAME_MOVE.BOT_GAME_ID,
                BOT_GAME_MOVE.POSITION,
                BOT_GAME_MOVE.UCI
            )
            .from(BOT_GAME_MOVE)
            .where(conditionForPairs)
            .awaitMappedRecords()
    }

    suspend fun listMoves(gameId: String): List<String> {
        return dslContext
            .select(BOT_GAME_MOVE.UCI)
            .from(BOT_GAME_MOVE)
            .where(BOT_GAME_MOVE.BOT_GAME_ID.eq(gameId))
            .orderBy(BOT_GAME_MOVE.POSITION)
            .awaitMappedRecords()
    }

    suspend fun countTotalGames(minIndex: Int): Int {
        return dslContext
            .selectCount()
            .from(BOT_GAME)
            .where(BOT_GAME.CURRENT_HALF_MOVE_INDEX.ge(minIndex).or(BOT_GAME.GAME_STATUS.`in`(CHECKMATED, STALEMATED)))
            .awaitSingleValue()!!
    }

    suspend fun countManchuGames(minIndex: Int): Int {
        return dslContext
            .selectCount()
            .from(BOT_GAME)
            .where(
                BOT_GAME.CURRENT_HALF_MOVE_INDEX.ge(minIndex)
                    .or(BOT_GAME.GAME_STATUS.`in`(CHECKMATED, STALEMATED))
                    .and(BOT_GAME.VARIANT.eq(Variant.MANCHU))
            )
            .awaitSingleValue()!!
    }

    suspend fun countTotalMoves(): Int {
        return dslContext
            .selectCount()
            .from(BOT_GAME_MOVE)
            .awaitSingleValue()!!
    }

    suspend fun countTotalMoves(gameId: String): Int {
        return dslContext
            .selectCount()
            .from(BOT_GAME_MOVE)
            .where(BOT_GAME_MOVE.BOT_GAME_ID.eq(gameId))
            .awaitSingleValue()!!
    }

    suspend fun saveFirstBotMove(gameId: String, botMove: BotMove, newFen: String) {
        dslContext.transactionCoroutine { cfg ->
            val now = Clock.System.now()

            DSL
                .using(cfg)
                .update(BOT_GAME)
                .set(BOT_GAME.CURRENT_HALF_MOVE_INDEX.fixed(), 1)
                .set(BOT_GAME.CURRENT_FEN.fixed(), newFen)
                .set(BOT_GAME.LAST_UPDATED.fixed(), now)
                .where(BOT_GAME.ID.eq(gameId))
                .awaitExecute()

            val botMoveRecord = BotGameMove()
            botMoveRecord.botGameId = gameId
            botMoveRecord.position = 0
            botMoveRecord.uci = botMove.uci
            if (botMove.moveType == BotGameMoveType.OPENING) {
                botMoveRecord.fromOpeningRepository = true
            }
            botMoveRecord.eventTime = now
            BotGameMoveDao(cfg).insertReactive(botMoveRecord)
        }
    }

    suspend fun saveUserMoveResult(
        gameId: String,
        userMove: String,
        callback: suspend (gameRecord: BotGame, userMove: String) -> PlayMoveBotGameCallbackResult,
    ) {
        dslContext.transactionCoroutine { cfg ->
            val transactional = DSL.using(cfg)

            transactional
                .select()
                .from(BOT_GAME)
                .where(BOT_GAME.ID.eq(gameId))
                .awaitMappedRecords<BotGame>()
                .firstOrNull()
                ?.let { game ->
                    val beforeCallbackTime = Clock.System.now()
                    val playMoveResult = callback(game, userMove)
                    val afterCallbackTime = Clock.System.now()

                    if (!playMoveResult.hasErrors()) {
                        val botGameMoveDao = BotGameMoveDao(cfg)

                        // persist user move
                        val userMoveRecord = BotGameMove()
                        userMoveRecord.botGameId = gameId
                        userMoveRecord.position = game.currentHalfMoveIndex
                        userMoveRecord.uci = userMove
                        userMoveRecord.eventTime = beforeCallbackTime
                        botGameMoveDao.insertReactive(userMoveRecord)

                        // persist engine move if any
                        playMoveResult.botMove?.let { botMove ->
                            val botMoveRecord = BotGameMove()
                            botMoveRecord.botGameId = gameId
                            botMoveRecord.position = game.currentHalfMoveIndex + 1
                            botMoveRecord.uci = botMove.uci
                            if (botMove.moveType == BotGameMoveType.OPENING) {
                                botMoveRecord.fromOpeningRepository = true
                            }
                            botMoveRecord.eventTime = afterCallbackTime
                            botGameMoveDao.insertReactive(botMoveRecord)
                        }

                        // persist status update (checkmate or stalemate) if any
                        playMoveResult.gameEventType?.let { gameEventType ->
                            val eventRecord = BotGameStatusEvent()
                            eventRecord.botGameId = gameId
                            eventRecord.eventType = gameEventType
                            eventRecord.eventTime = afterCallbackTime
                            BotGameStatusEventDao(cfg).insertReactive(eventRecord)
                        }

                        // update game record
                        val newPosition =
                            if (playMoveResult.botMove != null) {
                                game.currentHalfMoveIndex + 2
                            } else {
                                game.currentHalfMoveIndex + 1
                            }

                        var update =
                            transactional
                                .update(BOT_GAME)
                                .set(BOT_GAME.CURRENT_FEN.fixed(), playMoveResult.newFen)
                                .set(BOT_GAME.CURRENT_HALF_MOVE_INDEX.fixed(), newPosition)
                                .set(BOT_GAME.LAST_UPDATED.fixed(), afterCallbackTime)

                        playMoveResult.gameEventType?.let { gameEventType ->
                            update = update.set(BOT_GAME.GAME_STATUS.fixed(), gameEventType)
                        }

                        playMoveResult.outcome?.let { outcome ->
                            update = update.set(BOT_GAME.OUTCOME.fixed(), outcome)
                        }

                        update
                            .where(BOT_GAME.ID.eq(gameId))
                            .awaitExecute()
                    } else {
                        logger.warn { "error while playing move ${playMoveResult.errors.joinToString(", ")}, move not persisted" }
                    }
                }
        }
    }

    suspend fun updateGameStatus(
        gameId: String,
        status: GameEventType,
        winnerColor: Color? = null,
        eventTime: Instant = Clock.System.now()
    ) {
        dslContext.transactionCoroutine { cfg ->
            val eventRecord = BotGameStatusEvent()
            eventRecord.botGameId = gameId
            eventRecord.eventType = status
            eventRecord.eventTime = eventTime
            BotGameStatusEventDao(cfg).insertReactive(eventRecord)

            var update =
                DSL
                    .using(cfg)
                    .update(BOT_GAME)
                    .set(BOT_GAME.GAME_STATUS.fixed(), status)
                    .set(BOT_GAME.LAST_UPDATED.fixed(), eventTime)

            update = when (winnerColor) {
                Color.RED -> update.set(BOT_GAME.OUTCOME.fixed(), Outcome.RED_WINS)
                Color.BLACK -> update.set(BOT_GAME.OUTCOME.fixed(), Outcome.BLACK_WINS)
                null -> update
            }

            update
                .where(BOT_GAME.ID.eq(gameId))
                .awaitExecute()
        }
    }

    suspend fun transferFromGuestToUser(guestUserId: String, newUserId: String) {
        dslContext
            .update(BOT_GAME)
            .set(BOT_GAME.USER_ID, newUserId)
            .set(BOT_GAME.GUEST_USER_ID, guestUserId)
            .where(BOT_GAME.USER_ID.eq(guestUserId))
            .awaitExecute()
    }

}
