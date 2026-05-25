package io.elephantchess.db.services

import io.elephantchess.db.callback.PerpetualCheckingCallbackResult
import io.elephantchess.db.callback.PlayMoveCallbackResult
import io.elephantchess.db.callback.UpdateRatingsCallbackResult
import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.GameDao
import io.elephantchess.db.dao.codegen.tables.daos.GameMoveDao
import io.elephantchess.db.dao.codegen.tables.daos.GameStatusEventDao
import io.elephantchess.db.dao.codegen.tables.pojos.Game
import io.elephantchess.db.dao.codegen.tables.pojos.GameMove
import io.elephantchess.db.dao.codegen.tables.pojos.GameStatusEvent
import io.elephantchess.db.dao.codegen.tables.records.GameRecord
import io.elephantchess.db.dao.codegen.tables.records.UserRecord
import io.elephantchess.db.model.*
import io.elephantchess.db.model.analytics.PvpJoinSourceRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.*
import io.elephantchess.model.AnalysisStatus.CANCELLED
import io.elephantchess.model.AnalysisStatus.STARTED
import io.elephantchess.model.GameEventType.*
import io.elephantchess.model.GameEventType.Companion.gameEndedStatuses
import io.elephantchess.model.GameEventType.Companion.inProgressStatuses
import io.elephantchess.utils.TryEither
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactor.awaitSingle
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import reactor.core.publisher.Flux
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class PlayerVsPlayerGameDaoService(private val dslContext: DSLContext) {

    private val logger = KotlinLogging.logger {}

    suspend fun insertGame(userId: String, game: Game) {
        dslContext.transactionCoroutine { cfg ->
            GameDao(cfg).insertReactive(game)

            val gameStatusEvent = GameStatusEvent()
            gameStatusEvent.gameId = game.id
            gameStatusEvent.eventType = CREATED
            gameStatusEvent.userId = userId
            GameStatusEventDao(cfg).insertReactive(gameStatusEvent)
        }
    }

    suspend fun listCompatibleGames(
        inviterColor: Color?,
        isRated: Boolean,
        timeControlMode: TimeControlMode,
        timeControlBase: Int,
        timeControlIncrement: Int?,
        userType: UserType,
        userId: String,
        variant: Variant,
    ): List<Game> {
        var query =
            dslContext
                .select()
                .from(GAME)
                .where(GAME.GAME_STATUS.eq(CREATED))
                .and(GAME.TIME_CONTROL_MODE.eq(timeControlMode))
                .and(GAME.TIME_CONTROL_BASE.eq(timeControlBase))
                .and(GAME.IS_RATED.eq(isRated))
                .and(GAME.INVITER.notEqual(userId))
                .and(GAME.PRIVATE_INVITE.eq(false))
                .and(GAME.VARIANT.eq(variant))

        query = when (timeControlIncrement) {
            null -> query.and(GAME.TIME_CONTROL_INCREMENT.isNull)
            else -> query.and(GAME.TIME_CONTROL_INCREMENT.eq(timeControlIncrement))
        }

        query = when (inviterColor) {
            Color.RED -> query.and(GAME.INVITER_COLOR.eq(Color.BLACK))
            Color.BLACK -> query.and(GAME.INVITER_COLOR.eq(Color.RED))
            else -> query
        }

        query = when (userType) {
            UserType.GUEST -> query.and(GAME.ALLOW_GUESTS_TO_JOIN.eq(true))
            else -> query
        }

        return query.awaitMappedRecords()
    }

    suspend fun listGamesOpenToJoin(userType: UserType? = null): List<Game> {
        var query = dslContext
            .select()
            .from(GAME)
            .where(GAME.GAME_STATUS.eq(CREATED))
            .and(GAME.PRIVATE_INVITE.eq(false))

        query = when (userType) {
            UserType.GUEST -> query.and(GAME.ALLOW_GUESTS_TO_JOIN.eq(true))
            else -> query
        }

        return query.awaitMappedRecords<Game>()
    }

    suspend fun listGameByUserId(userId: String, limit: Int, beforeTs: Long?): List<Game> {
        var sql = dslContext
            .select()
            .from(GAME)
            .where(isPlaying(userId))

        if (beforeTs != null) {
            sql = sql.and(GAME.LAST_UPDATED.isBeforeEpochMillis(beforeTs))
        }

        return sql
            .orderBy(GAME.LAST_UPDATED.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

    suspend fun listLastGamesByUserId(userId: String, limit: Int, minMoveIndex: Int, beforeTs: Long?): List<Game> {
        var sql = dslContext
            .select()
            .from(GAME)
            .where(isPlaying(userId))
            .and(GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex))
            .and(GAME.CONTAINS_ERRORS.eq(false))

        if (beforeTs != null) {
            sql = sql.and(GAME.LAST_UPDATED.isBeforeEpochMillis(beforeTs))
        }

        return sql
            .orderBy(GAME.LAST_UPDATED.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

    suspend fun listPotentiallyFlaggedGames(): List<Game> {
        val now = Clock.System.now()
        val games = mutableListOf<Game>()
        val fields =
            listOf(
                GAME.ID,
                GAME.TIME_CONTROL_MODE,
                GAME.TIME_CONTROL_BASE,
                GAME.TIME_CONTROL_INCREMENT
            )

        games += dslContext
            .select(fields)
            .from(GAME)
            .where(GAME.MIN_FLAG_CHECK_TIME.lessThan(now))
            .and(GAME.OUTCOME.isNull)
            .and(GAME.GAME_STATUS.`in`(inProgressStatuses))
            .and(GAME.TIME_CONTROL_MODE.eq(TimeControlMode.GAME_TIME))
            .orderBy(GAME.LAST_UPDATED.asc())
            .awaitMappedRecords<Game>()

        games += dslContext
            .select(fields)
            .from(GAME)
            .where(GAME.OUTCOME.isNull)
            .and(GAME.GAME_STATUS.`in`(inProgressStatuses))
            .and(GAME.TIME_CONTROL_MODE.eq(TimeControlMode.MOVE_TIME))
            .orderBy(GAME.LAST_UPDATED.asc())
            .awaitMappedRecords<Game>()

        return games
    }

    suspend fun listLastGames(
        limit: Int,
        statusToExcludes: List<GameEventType> = listOf(),
    ): List<Game> {
        val query =
            if (statusToExcludes.isNotEmpty()) {
                dslContext
                    .select()
                    .from(GAME)
                    .where(GAME.GAME_STATUS.notIn(statusToExcludes))
            } else {
                dslContext
                    .select()
                    .from(GAME)
            }

        return query
            .orderBy(GAME.LAST_UPDATED.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

    suspend fun listLastGames(limit: Int, minMoveIndex: Int, beforeTs: Long?): List<Game> {
        var sql = dslContext
            .select()
            .from(GAME)
            .where(GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex))
            .and(GAME.CONTAINS_ERRORS.eq(false))

        if (beforeTs != null) {
            sql = sql.and(GAME.LAST_UPDATED.isBeforeEpochMillis(beforeTs))
        }

        return sql
            .orderBy(GAME.LAST_UPDATED.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

    /**
     * List games with status CREATED where the inviter has been offline for "duration"
     */
    suspend fun listCreatedGamesByOfflineUsers(duration: Duration, userType: UserType): List<String> {
        return dslContext
            .select(GAME.ID)
            .from(GAME, USER)
            .where(GAME.INVITER.eq(USER.ID))
            .and(USER.USER_TYPE.eq(userType))
            .and(GAME.GAME_STATUS.eq(CREATED))
            .and(USER.LAST_ONLINE.isOlderThan(duration))
            .and(GAME.LAST_UPDATED.isOlderThan(duration))
            .awaitMappedRecords()
    }

    suspend fun listAllActiveGames(): List<Game> {
        return dslContext
            .selectFrom(GAME)
            .where(GAME.GAME_STATUS.`in`(inProgressStatuses))
            .awaitMappedRecords<Game>()
    }

    suspend fun listPreAnalysisToDelete(limit: Duration): List<Pair<String, Instant>> {
        return dslContext
            .select(
                GAME.ID,
                GAME.ANALYSIS_START_TIME
            )
            .from(GAME)
            .where(GAME.ANALYSIS_STATUS.`in`(STARTED, CANCELLED))
            .and(GAME.ANALYSIS_START_TIME.isOlderThan(limit))
            .awaitRecords()
            .map { record2 ->
                Pair(
                    record2.get(GAME.ID),
                    record2.get(GAME.ANALYSIS_START_TIME)
                )
            }
    }

    suspend fun latestGameActivity(minMoveIndex: Int = 0): Instant? {
        return dslContext
            .select(DSL.max(GAME.LAST_UPDATED))
            .from(GAME)
            .where(GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex))
            .and(GAME.GAME_STATUS.notIn(listOf(FLAGGED, AUTO_CANCELED)))
            .awaitSingleValue()
    }

    suspend fun latestMoveTime(minMoveIndex: Int = 0): Instant? {
        return dslContext
            .select(DSL.max(GAME_MOVE.EVENT_TIME))
            .from(GAME, GAME_MOVE)
            .where(GAME.ID.eq(GAME_MOVE.GAME_ID))
            .and(GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex))
            .awaitSingleValue()
    }

    suspend fun countCreatedGamesByUser(
        userId: String,
        timeControlCategory: TimeControlCategory,
    ): Int {
        return dslContext
            .selectCount()
            .from(GAME)
            .where(GAME.INVITER.eq(userId))
            .and(GAME.GAME_STATUS.eq(CREATED))
            .and(GAME.TIME_CONTROL_CATEGORY.eq(timeControlCategory))
            .awaitSingleValue() ?: 0
    }

    suspend fun countLiveGames(duration: Duration): Int {
        return dslContext
            .selectCount()
            .from(GAME)
            .where(GAME.GAME_STATUS.`in`(inProgressStatuses))
            .and(GAME.LAST_UPDATED.isWithin(duration))
            .awaitSingleValue()!!
    }

    suspend fun fetchById(gameId: String): Game? {
        return dslContext
            .selectFrom(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleMappedRecord()
    }

    suspend fun listMoves(gameId: String): List<String> {
        return listMoves(dslContext, gameId)
    }

    suspend fun countTotalGames(minMoveIndex: Int): Int {
        return dslContext
            .selectCount()
            .from(GAME)
            .where(GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex).or(GAME.GAME_STATUS.`in`(CHECKMATED, STALEMATED)))
            .awaitSingleValue()!!
    }

    suspend fun countManchuGames(minMoveIndex: Int): Int {
        val countCondition =
            GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex)
                .or(GAME.GAME_STATUS.`in`(CHECKMATED, STALEMATED))

        return dslContext
            .selectCount()
            .from(GAME)
            .where(countCondition.and(GAME.VARIANT.eq(Variant.MANCHU)))
            .awaitSingleValue()!!
    }

    suspend fun countTotalMoves(): Int {
        return dslContext
            .selectCount()
            .from(GAME_MOVE)
            .awaitSingleValue()!!
    }

    suspend fun countTotalMoves(gameId: String): Int {
        return dslContext
            .selectCount()
            .from(GAME_MOVE)
            .where(GAME_MOVE.GAME_ID.eq(gameId))
            .awaitSingleValue()!!
    }

    suspend fun fetchTimedMoveHistory(gameId: String): List<GameMove> {
        return dslContext
            .select()
            .from(GAME_MOVE)
            .where(GAME_MOVE.GAME_ID.eq(gameId))
            .orderBy(GAME_MOVE.POSITION)
            .awaitMappedRecords()
    }

    suspend fun fetchLastMove(gameId: String): GameMove? {
        return dslContext
            .select()
            .from(GAME_MOVE)
            .where(GAME_MOVE.GAME_ID.eq(gameId))
            .orderBy(GAME_MOVE.POSITION.desc())
            .limit(1)
            .awaitSingleMappedRecord()
    }

    suspend fun fetchJoinTime(gameId: String): Instant? {
        // FIXME: there should be only one but added limit(1) because
        //  org.jooq.exception.TooManyRowsException was raised one
        //  wh would there be 2 JOINED time?
        return dslContext
            .select(GAME_STATUS_EVENT.EVENT_TIME)
            .from(GAME_STATUS_EVENT)
            .where(GAME_STATUS_EVENT.GAME_ID.eq(gameId))
            .and(GAME_STATUS_EVENT.EVENT_TYPE.eq(JOINED))
            .orderBy(GAME_STATUS_EVENT.EVENT_TIME.desc())
            .limit(1)
            .awaitSingleValue()
    }

    suspend fun fetchGameEndTime(gameId: String): Instant? {
        // FIXME: there should be only one but added limit(1) because
        //  org.jooq.exception.TooManyRowsException was raised one
        return dslContext
            .select(GAME_STATUS_EVENT.EVENT_TIME)
            .from(GAME_STATUS_EVENT)
            .where(GAME_STATUS_EVENT.GAME_ID.eq(gameId))
            .and(GAME_STATUS_EVENT.EVENT_TYPE.`in`(gameEndedStatuses))
            .orderBy(GAME_STATUS_EVENT.EVENT_TIME.desc())
            .limit(1)
            .awaitSingleValue()
    }

    suspend fun fetchGameStatus(gameId: String): GameEventType? {
        return dslContext
            .select(GAME.GAME_STATUS)
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleValue()
    }

    suspend fun fetchCurrentStatusAndFen(gameIds: List<String>): List<Game> {
        return if (gameIds.isEmpty()) {
            emptyList()
        } else {
            dslContext
                .select(
                    GAME.ID,
                    GAME.GAME_STATUS,
                    GAME.CURRENT_FEN,
                    GAME.LAST_UPDATED
                )
                .from(GAME)
                .where(GAME.ID.`in`(gameIds))
                .awaitMappedRecords<Game>()
        }
    }

    suspend fun fetchRatingForUser(userId: String, timeControlCategory: TimeControlCategory, variant: Variant): Int? {
        return fetchRatingForUser(dslContext, userId, timeControlCategory, variant)
    }

    private suspend fun fetchRatingForUser(
        context: DSLContext,
        userId: String,
        timeControlCategory: TimeControlCategory,
        variant: Variant,
    ): Int? {
        return context
            .select(findRatingField(timeControlCategory, variant))
            .from(USER)
            .where(USER.ID.eq((userId)))
            .awaitSingleValue()
    }

    suspend fun fetchRatingUpdate(gameId: String): Game? {
        return dslContext
            .select(
                GAME.IS_RATED,
                GAME.INVITER_RATING_FROM,
                GAME.INVITER_RATING_TO,
                GAME.INVITEE_RATING_FROM,
                GAME.INVITEE_RATING_TO
            )
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleMappedRecord()
    }

    suspend fun fetchHasJoinedData(gameId: String): HasJoinedRecord? {
        return dslContext
            .select(
                GAME.INVITEE,
                GAME.INVITER_COLOR,
                GAME.INVITER_RATING_FROM,
                GAME.INVITEE_RATING_FROM
            )
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleRecord()
            ?.let { record ->
                HasJoinedRecord(
                    gameId = gameId,
                    invitee = record.get(GAME.INVITEE),
                    inviterColor = record.get(GAME.INVITER_COLOR),
                    inviterRating = record.get(GAME.INVITER_RATING_FROM),
                    inviteeRating = record.get(GAME.INVITEE_RATING_FROM)
                )
            }
    }

    suspend fun fetchInviterColor(gameId: String): Color? {
        return dslContext
            .select(GAME.INVITER_COLOR)
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleValue()
    }

    suspend fun fetchMoveAt(gameId: String, index: Int): GameMove? {
        return dslContext
            .select()
            .from(GAME_MOVE)
            .where(GAME_MOVE.GAME_ID.eq(gameId))
            .and(GAME_MOVE.POSITION.eq(index))
            .awaitSingleMappedRecord()
    }

    suspend fun fetchGameStates(gameIds: List<String>): Map<String, GameStateResult> {
        // TODO: Flux.from() -> can we not use our shortcut thingy?
        return Flux.from(
            dslContext
                .select(
                    GAME.ID,
                    GAME.CURRENT_FEN,
                    GAME.CURRENT_HALF_MOVE_INDEX,
                    GAME.GAME_STATUS
                )
                .from(GAME)
                .where(GAME.ID.`in`(gameIds))
        )
            .collectList()
            .awaitSingle()
            .associate { record ->
                record.get(GAME.ID) to GameStateResult(
                    fen = record.get(GAME.CURRENT_FEN),
                    index = record.get(GAME.CURRENT_HALF_MOVE_INDEX),
                    gameEventType = record.get(GAME.GAME_STATUS)
                )
            }
    }

    suspend fun fetchDrawPropositionUser(gameId: String): String? {
        return dslContext
            .select(GAME.DRAW_PROPOSITION_USER)
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleValue()
    }

    suspend fun fetchPlayersAndStatus(gameId: String): GamePlayersStatus? {
        return dslContext
            .select(
                GAME.INVITER,
                GAME.INVITEE,
                GAME.GAME_STATUS,
                GAME.ALLOW_GUESTS_TO_JOIN
            )
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleRecord()
            ?.let { record ->
                GamePlayersStatus(
                    inviterId = record.get(GAME.INVITER),
                    inviteeId = record.get(GAME.INVITEE),
                    status = record.get(GAME.GAME_STATUS),
                    allowGuests = record.get(GAME.ALLOW_GUESTS_TO_JOIN)
                )
            }
    }

    suspend fun fetchUserIdForColor(gameId: String, color: Color): String? {
        return dslContext
            .select(
                GAME.INVITER,
                GAME.INVITEE,
                GAME.INVITER_COLOR
            )
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleMappedRecord<Game>()
            ?.colorUser(color)
    }

    suspend fun fetchTimeControl(gameId: String): TimeControlRecord? {
        return dslContext
            .select(
                GAME.TIME_CONTROL_MODE,
                GAME.TIME_CONTROL_BASE,
                GAME.TIME_CONTROL_INCREMENT
            )
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleRecord()
            ?.let { record ->
                if (record.get(GAME.TIME_CONTROL_BASE) == null) {
                    null
                } else {
                    TimeControlRecord(
                        mode = record.get(GAME.TIME_CONTROL_MODE),
                        base = record.get(GAME.TIME_CONTROL_BASE),
                        increment = record.get(GAME.TIME_CONTROL_INCREMENT) ?: 0
                    )
                }
            }
    }

    suspend fun fetchTimeControlCategory(gameId: String): TimeControlCategory? {
        return dslContext
            .select(GAME.TIME_CONTROL_CATEGORY)
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleValue()
    }

    suspend fun fetchVariant(gameId: String): Variant? {
        return dslContext
            .select(GAME.VARIANT)
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleValue()
    }

    /**
     * Returns email if we should send a notification, null otherwise
     */
    suspend fun shouldSendGameJoinedNotification(gameId: String, duration: Duration): String? {
        return shouldSendNotification(
            gameId = gameId,
            duration = duration,
            allowNotificationField = USER.EMAIL_NOTIFICATION_ENABLED_USER_JOINED_GAME,
            userIdField = GAME.INVITER
        )
    }

    /**
     * Returns email if we should send a notification, null otherwise
     */
    suspend fun shouldSendOpponentPlayedMoveNotification(
        gameId: String,
        userId: String,
        gamePlayersStatus: GamePlayersStatus,
        duration: Duration,
    ): String? {
        return shouldSendNotification(
            gameId = gameId,
            duration = duration,
            allowNotificationField = USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PLAYED_MOVE,
            userIdField = findOpponentUserIdField(gamePlayersStatus, userId)
        )
    }

    /**
     * Returns email if we should send a notification, null otherwise
     */
    suspend fun shouldSendGameResignedNotification(
        gameId: String,
        userId: String,
        gamePlayersStatus: GamePlayersStatus,
        duration: Duration,
    ): String? {
        return shouldSendNotification(
            gameId = gameId,
            duration = duration,
            allowNotificationField = USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_RESIGNED,
            userIdField = findOpponentUserIdField(gamePlayersStatus, userId)
        )
    }

    /**
     * Returns email if we should send a notification, null otherwise
     */
    suspend fun shouldSendOpponentProposedDrawNotification(
        gameId: String,
        userId: String,
        gamePlayersStatus: GamePlayersStatus,
        duration: Duration,
    ): String? {
        return shouldSendNotification(
            gameId = gameId,
            duration = duration,
            allowNotificationField = USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PROPOSED_DRAW,
            userIdField = findOpponentUserIdField(gamePlayersStatus, userId)
        )
    }

    /**
     * Returns email if we should send a notification, null otherwise
     */
    suspend fun shouldSendResponseToDrawNotification(
        accept: Boolean,
        gameId: String,
        userId: String,
        gamePlayersStatus: GamePlayersStatus,
        duration: Duration,
    ): String? {
        val field =
            if (accept) {
                USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_ACCEPTED_DRAW
            } else {
                USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_DECLINED_DRAW
            }

        return shouldSendNotification(
            gameId = gameId,
            duration = duration,
            allowNotificationField = field,
            userIdField = findOpponentUserIdField(gamePlayersStatus, userId)
        )
    }

    private suspend fun shouldSendNotification(
        gameId: String,
        duration: Duration,
        allowNotificationField: TableField<UserRecord, Boolean>,
        userIdField: TableField<GameRecord, String>?,
    ): String? {
        if (userIdField == null) {
            return null
        }

        return dslContext
            .select(USER.EMAIL)
            .from(GAME, USER)
            .where(GAME.ID.eq(gameId))
            .and(userIdField.eq(USER.ID))
            .and(allowNotificationField.eq(true))
            .and(USER.LAST_ONLINE.isOlderThan(duration))
            .awaitSingleValue()
    }

    suspend fun joinGame(
        userId: String,
        userRating: Int,
        gameId: String,
        updatedInviterColor: Color?,
        timeControlRecord: TimeControlRecord?,
        source: GameJoinSource?,
        sourceId: String?,
    ) {
        dslContext.transactionCoroutine { cfg ->
            val now = Clock.System.now()

            var update =
                DSL
                    .using(cfg)
                    .update(GAME)
                    .set(GAME.INVITEE.fixed(), userId)
                    .set(GAME.INVITEE_RATING_FROM.fixed(), userRating)
                    .set(GAME.GAME_STATUS.fixed(), JOINED)
                    .set(GAME.JOIN_SOURCE.fixed(), source)
                    .set(GAME.JOIN_SOURCE_ID.fixed(), sourceId)
                    .set(GAME.LAST_UPDATED.fixed(), now)

            if (updatedInviterColor != null) {
                update = update.set(GAME.INVITER_COLOR.fixed(), updatedInviterColor)
            }

            if (timeControlRecord != null) {
                update = update.set(GAME.MIN_FLAG_CHECK_TIME.fixed(), now.plusSeconds(timeControlRecord.base.toLong()))
            }

            update
                .where(GAME.ID.eq(gameId))
                .awaitExecute()

            val gameStatusEvent = GameStatusEvent()
            gameStatusEvent.gameId = gameId
            gameStatusEvent.eventType = JOINED
            gameStatusEvent.userId = userId
            gameStatusEvent.eventTime = now
            GameStatusEventDao(cfg).insertReactive(gameStatusEvent)
        }
    }

    suspend fun updateStatus(userId: String?, gameId: String, status: GameEventType, outcome: Outcome? = null) {
        dslContext.transactionCoroutine { cfg ->
            val now = Clock.System.now()

            var update =
                DSL
                    .using(cfg)
                    .update(GAME)
                    .set(GAME.GAME_STATUS.fixed(), status)
                    .set(GAME.LAST_UPDATED.fixed(), now)

            update = when (status) {
                DRAW_PROPOSED -> update.set(GAME.DRAW_PROPOSITION_USER.fixed(), userId)
                DRAW_DECLINED -> update.setNull(GAME.DRAW_PROPOSITION_USER.fixed())
                DRAW_ACCEPTED -> update.setNull(GAME.DRAW_PROPOSITION_USER.fixed())
                else -> update
            }

            if (outcome != null) {
                update = update.set(GAME.OUTCOME.fixed(), outcome)
            }

            update
                .where(GAME.ID.eq(gameId))
                .awaitExecute()

            val gameStatusEvent = GameStatusEvent()
            gameStatusEvent.gameId = gameId
            gameStatusEvent.eventType = status
            gameStatusEvent.userId = userId
            gameStatusEvent.eventTime = now
            GameStatusEventDao(cfg).insertReactive(gameStatusEvent)
        }
    }

    suspend fun resign(
        userId: String,
        gameId: String,
        updateRatingsCallback: suspend (Game, Outcome, Int, Int) -> UpdateRatingsCallbackResult?,
    ) {
        dslContext.transactionCoroutine { cfg ->
            val transactional = DSL.using(cfg)
            val gameRecord = fetchGameInProgress(transactional, gameId)
            if (gameRecord != null) {
                // userId resigns, their opponent wins
                val winnerColor = gameRecord.opponentColorOf(userId)!!
                val outcome = winnerColor.asWinnerOutcome()
                persistVictory(transactional, userId, RESIGNED, gameRecord, outcome, updateRatingsCallback)
            } else {
                logger.warn { "[$gameId] game record not found, game already marked resigned or already has outcome" }
            }
        }
    }

    suspend fun flag(
        userId: String,
        gameId: String,
        winnerColor: Color,
        updateRatingsCallback: suspend (Game, Outcome, Int, Int) -> UpdateRatingsCallbackResult?,
    ) {
        dslContext.transactionCoroutine { cfg ->
            val transactional = DSL.using(cfg)
            val gameRecord = fetchGameInProgress(transactional, gameId)
            if (gameRecord != null) {
                val outcome = winnerColor.asWinnerOutcome()
                persistVictory(transactional, userId, FLAGGED, gameRecord, outcome, updateRatingsCallback)
            } else {
                logger.warn { "[$gameId] game record not found, game already marked flagged or already has outcome" }
            }
        }
    }

    suspend fun saveMove(
        userId: String,
        gameId: String,
        move: String,
        playMoveCallback: (Game) -> PlayMoveCallbackResult,
        hasViolatedPerpetualCheckingRule: (Game, List<String>) -> PerpetualCheckingCallbackResult?,
        updateRatingsCallback: (Game, Outcome, Int, Int) -> UpdateRatingsCallbackResult?,
    ): TryEither<PlayMoveCallbackResult> {
        return dslContext.transactionalContextTry { transactional ->
            transactional
                .select()
                .from(GAME)
                .where(GAME.ID.eq(gameId))
                .awaitMappedRecords<Game>()
                .first()
                .let { gameRecord ->
                    var playMoveResult = playMoveCallback(gameRecord)
                    val now = Clock.System.now()

                    suspend fun persistVictory(newGameEventType: GameEventType, newOutcome: Outcome) {
                        persistVictory(
                            transactional = transactional,
                            userId = userId,
                            gameEventType = newGameEventType,
                            gameRecord = gameRecord,
                            outcome = newOutcome,
                            updateRatingsCallback = updateRatingsCallback,
                            now = now
                        )
                    }

                    // save move
                    val moveRecord = GameMove()
                    moveRecord.gameId = gameId
                    moveRecord.position = gameRecord.currentHalfMoveIndex
                    moveRecord.uci = move
                    moveRecord.eventTime = now
                    GameMoveDao(transactional.configuration()).insertReactive(moveRecord)

                    if (playMoveResult.isMated()) {
                        // save victory if mated
                        persistVictory(playMoveResult.newGameEventType!!, playMoveResult.outcome!!)
                    } else if (playMoveResult.mustCheckPerpetualChecking) {
                        // check if violated perpetual checking rule
                        val moves = listMoves(transactional, gameId)
                        hasViolatedPerpetualCheckingRule(gameRecord, moves)?.let { perpetualCheckingResult ->
                            persistVictory(perpetualCheckingResult.newGameEventType, perpetualCheckingResult.outcome)
                            playMoveResult = playMoveResult.copy(
                                newGameEventType = perpetualCheckingResult.newGameEventType,
                                outcome = perpetualCheckingResult.outcome
                            )
                        }
                    }

                    // update game record
                    transactional
                        .update(GAME)
                        .set(GAME.CURRENT_FEN.fixed(), playMoveResult.newFen)
                        .set(GAME.CURRENT_HALF_MOVE_INDEX.fixed(), playMoveResult.newPosition)
                        .set(GAME.LAST_UPDATED.fixed(), now)
                        .where(GAME.ID.eq(gameId))
                        .awaitExecute()

                    playMoveResult
                }
        }
    }

    /**
     * userId isn't necessarily the winner, it's the user who made the move or the action (like flagging or resigning)
     */
    private suspend fun persistVictory(
        transactional: DSLContext,
        userId: String,
        gameEventType: GameEventType,
        gameRecord: Game,
        outcome: Outcome,
        updateRatingsCallback: suspend (Game, Outcome, Int, Int) -> UpdateRatingsCallbackResult?,
        now: Instant = Clock.System.now(),
    ) {
        suspend fun persistNewStatus() {
            // insert status event
            transactional
                .insertInto(GAME_STATUS_EVENT)
                .set(GAME_STATUS_EVENT.GAME_ID.fixed(), gameRecord.id)
                .set(GAME_STATUS_EVENT.EVENT_TYPE.fixed(), gameEventType)
                .set(GAME_STATUS_EVENT.USER_ID.fixed(), userId)
                .set(GAME_STATUS_EVENT.EVENT_TIME.fixed(), now)
                .awaitExecute()

            // update status and outcome
            transactional
                .update(GAME)
                .set(GAME.GAME_STATUS.fixed(), gameEventType)
                .set(GAME.OUTCOME.fixed(), outcome)
                .set(GAME.LAST_UPDATED.fixed(), now)
                .where(GAME.ID.eq(gameRecord.id))
                .awaitExecute()
        }

        suspend fun persistRatingUpdate() {
            val timeControlCategory = gameRecord.timeControlCategory!!
            val variant = gameRecord.variant
            val ratingField = findRatingField(timeControlCategory, variant)
            // we use the current rating instead of the rating the user had at the beginning of the game
            val inviterRating = fetchRatingForUser(transactional, gameRecord.inviter, timeControlCategory, variant)
            val inviteeRating = fetchRatingForUser(transactional, gameRecord.invitee, timeControlCategory, variant)
            val updatedRatings = updateRatingsCallback(gameRecord, outcome, inviterRating!!, inviteeRating!!)

            suspend fun updateUserRating(userId: String, rating: Int) {
                transactional
                    .update(USER)
                    .set(ratingField.fixed(), rating)
                    .where(USER.ID.eq(userId))
                    .awaitExecute()
            }

            if (updatedRatings != null) {
                // the start rating is overridden,
                // since it may have changed since the beginning of the game, if it was updated in another game
                transactional
                    .update(GAME)
                    .set(GAME.INVITER_RATING_FROM.fixed(), inviterRating)
                    .set(GAME.INVITEE_RATING_FROM.fixed(), inviteeRating)
                    .set(GAME.INVITER_RATING_TO.fixed(), updatedRatings.inviterNewRating)
                    .set(GAME.INVITEE_RATING_TO.fixed(), updatedRatings.inviteeNewRating)
                    .where(GAME.ID.eq(gameRecord.id))
                    .awaitExecute()

                updateUserRating(userId = gameRecord.inviter, rating = updatedRatings.inviterNewRating)
                updateUserRating(gameRecord.invitee, updatedRatings.inviteeNewRating)
            }
        }

        persistNewStatus()
        persistRatingUpdate()
    }

    private suspend fun listMoves(context: DSLContext, gameId: String): List<String> {
        return context
            .select(GAME_MOVE.UCI)
            .from(GAME_MOVE)
            .where(GAME_MOVE.GAME_ID.eq(gameId))
            .orderBy(GAME_MOVE.POSITION)
            .awaitMappedRecords()
    }

    private suspend fun fetchGameInProgress(context: DSLContext, gameId: String): Game? {
        return context
            .select()
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .and(GAME.OUTCOME.isNull)
            .and(GAME.GAME_STATUS.`in`(inProgressStatuses))
            .awaitSingleMappedRecord()
    }

    private companion object {

        fun findOpponentUserIdField(
            gamePlayersStatus: GamePlayersStatus,
            userId: String?,
        ): TableField<GameRecord, String>? {
            if (userId == null) {
                return null
            }

            val opponentUserId = gamePlayersStatus.getOpponentUserId(userId)
            return findUserIdField(gamePlayersStatus, opponentUserId)
        }

        fun findUserIdField(
            gamePlayersStatus: GamePlayersStatus,
            userId: String?,
        ): TableField<GameRecord, String>? {
            if (userId == null) {
                return null
            }

            var field: TableField<GameRecord, String>? = null

            if (gamePlayersStatus.isInviter(userId)) {
                field = GAME.INVITER
            } else if (gamePlayersStatus.isInvitee(userId)) {
                field = GAME.INVITEE
            }

            return field
        }

        fun findRatingField(timeControlCategory: TimeControlCategory, variant: Variant): TableField<UserRecord, Int> {
            return when (variant) {
                Variant.XIANGQI -> when (timeControlCategory) {
                    TimeControlCategory.BULLET -> USER.GAME_RATING_BULLET
                    TimeControlCategory.BLITZ -> USER.GAME_RATING_BLITZ
                    TimeControlCategory.RAPID -> USER.GAME_RATING_RAPID
                    TimeControlCategory.CLASSICAL -> USER.GAME_RATING_CLASSICAL
                    TimeControlCategory.SEVERAL_DAYS -> USER.GAME_RATING_SEVERAL_DAYS
                    TimeControlCategory.CORRESPONDENCE -> USER.GAME_RATING_CORRESPONDENCE
                }

                Variant.MANCHU -> when (timeControlCategory) {
                    TimeControlCategory.BULLET -> USER.GAME_RATING_MANCHU_BULLET
                    TimeControlCategory.BLITZ -> USER.GAME_RATING_MANCHU_BLITZ
                    TimeControlCategory.RAPID -> USER.GAME_RATING_MANCHU_RAPID
                    TimeControlCategory.CLASSICAL -> USER.GAME_RATING_MANCHU_CLASSICAL
                    TimeControlCategory.SEVERAL_DAYS -> USER.GAME_RATING_MANCHU_SEVERAL_DAYS
                    TimeControlCategory.CORRESPONDENCE -> USER.GAME_RATING_MANCHU_CORRESPONDENCE
                }
            }
        }

        fun isPlaying(userId: String): Condition {
            return GAME.INVITER.eq(userId).or(GAME.INVITEE.eq(userId))
        }
    }

    /**
     * Counts total PvP games grouped by month
     */
    suspend fun countTotalGamesByMonth(): List<MonthlyValueRecord> {
        val yearMonthField = GAME.CREATED.yearMonth()
        return dslContext
            .select(yearMonthField, DSL.count())
            .from(GAME)
            .groupBy(yearMonthField)
            .orderBy(yearMonthField)
            .awaitRecords()
            .map { MonthlyValueRecord.ofInt(it) }
    }

    /**
     * Counts PvP games with at least minMoveIndex moves, grouped by month
     */
    suspend fun countGamesOverMoveIndexByMonth(minMoveIndex: Int): List<MonthlyValueRecord> {
        val yearMonthField = GAME.CREATED.yearMonth()
        return dslContext
            .select(yearMonthField, DSL.count())
            .from(GAME)
            .where(GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex))
            .groupBy(yearMonthField)
            .orderBy(yearMonthField)
            .awaitRecords()
            .map { MonthlyValueRecord.ofInt(it) }
    }

    /**
     * Counts PvP games with at least minMoveIndex moves, grouped by month and join source
     */
    suspend fun countGamesOverMoveIndexByMonthAndJoinSource(minMoveIndex: Int): List<PvpJoinSourceRecord> {
        val yearMonthField = GAME.CREATED.yearMonth()
        return dslContext
            .select(yearMonthField, GAME.JOIN_SOURCE, DSL.count())
            .from(GAME)
            .where(GAME.CURRENT_HALF_MOVE_INDEX.ge(minMoveIndex))
            .groupBy(yearMonthField, GAME.JOIN_SOURCE)
            .orderBy(yearMonthField)
            .awaitRecords()
            .map { record ->
                PvpJoinSourceRecord(
                    month = record.value1()!!,
                    joinSource = record.value2()?.name ?: "UNKNOWN",
                    count = record.value3()
                )
            }
    }

    suspend fun transferFromGuestToUser(guestUserId: String, newUserId: String) {
        dslContext.transactionCoroutine { cfg ->
            val transaction = DSL.using(cfg)

            transaction.update(GAME)
                .set(GAME.INVITER, newUserId)
                .set(GAME.GUEST_USER_ID, guestUserId)
                .where(GAME.INVITER.eq(guestUserId))
                .awaitExecute()

            transaction.update(GAME)
                .set(GAME.INVITEE, newUserId)
                .set(GAME.GUEST_USER_ID, guestUserId)
                .where(GAME.INVITEE.eq(guestUserId))
                .awaitExecute()
        }
    }

}
