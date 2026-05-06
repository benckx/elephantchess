package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.SevenKingdomsGameDao
import io.elephantchess.db.dao.codegen.tables.daos.SevenKingdomsGameEventDao
import io.elephantchess.db.dao.codegen.tables.daos.SevenKingdomsGameMoveDao
import io.elephantchess.db.dao.codegen.tables.pojos.SevenKingdomsGame
import io.elephantchess.db.dao.codegen.tables.pojos.SevenKingdomsGameEvent
import io.elephantchess.db.dao.codegen.tables.pojos.SevenKingdomsGameMove
import io.elephantchess.db.dao.codegen.tables.records.SevenKingdomsGameRecord
import io.elephantchess.db.utils.*
import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameEventType.*
import io.elephantchess.sevenkingdoms.Color
import org.jooq.DSLContext
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock

class SevenKingdomsGameDaoService(private val dslContext: DSLContext) {

    suspend fun save(gameRecord: SevenKingdomsGame) =
        dslContext.transactionCoroutine { cfg ->
            SevenKingdomsGameDao(cfg).insertReactive(gameRecord)
        }

    suspend fun fetchGame(gameId: String): SevenKingdomsGame? =
        dslContext
            .selectFrom(SEVEN_KINGDOMS_GAME)
            .where(SEVEN_KINGDOMS_GAME.ID.eq(gameId))
            .awaitSingleMappedRecord()

    suspend fun countGamesWithStatusCreated(minPlayers: Int): Int =
        dslContext
            .selectCount()
            .from(SEVEN_KINGDOMS_GAME)
            .where(SEVEN_KINGDOMS_GAME.MIN_PLAYERS.eq(minPlayers))
            .and(SEVEN_KINGDOMS_GAME.GAME_STATUS.eq(CREATED))
            .awaitSingleValue() ?: 0

    suspend fun fetchMovesUci(gameId: String): List<String> =
        dslContext
            .select(SEVEN_KINGDOMS_GAME_MOVE.UCI)
            .from(SEVEN_KINGDOMS_GAME_MOVE)
            .where(SEVEN_KINGDOMS_GAME_MOVE.GAME_ID.eq(gameId))
            .orderBy(SEVEN_KINGDOMS_GAME_MOVE.POSITION)
            .awaitMappedRecords()

    suspend fun fetchEliminationEvents(gameId: String): List<SevenKingdomsGameEvent> =
        dslContext
            .select(SEVEN_KINGDOMS_GAME_EVENT.INDEX, SEVEN_KINGDOMS_GAME_EVENT.COLORS)
            .from(SEVEN_KINGDOMS_GAME_EVENT)
            .where(SEVEN_KINGDOMS_GAME_EVENT.GAME_ID.eq(gameId))
            .and(SEVEN_KINGDOMS_GAME_EVENT.EVENT_TYPE.`in`(listOf(RESIGNED, FLAGGED)))
            .awaitMappedRecords()

    suspend fun updateJoinStatus(
        userId: String,
        gameId: String,
        newColorsOfUser: List<Color>,
        newStatus: GameEventType
    ) {
        dslContext.transactionCoroutine { cfg ->
            val now = Clock.System.now()
            val transactional = DSL.using(cfg)

            // remove all colors allocations of user
            Color.entries.forEach { color ->
                transactional
                    .update(SEVEN_KINGDOMS_GAME)
                    .set(mapColorToField(color).fixed(), null as Color?)
                    .where(SEVEN_KINGDOMS_GAME.ID.eq(gameId))
                    .and(mapColorToField(color).eq(userId))
                    .awaitExecute()
            }

            // assign new colors to user
            newColorsOfUser.forEach { color ->
                transactional
                    .update(SEVEN_KINGDOMS_GAME)
                    .set(mapColorToField(color).fixed(), userId)
                    .set(SEVEN_KINGDOMS_GAME.GAME_STATUS, newStatus)
                    .where(SEVEN_KINGDOMS_GAME.ID.eq(gameId))
                    .awaitExecute()
            }

            // add game event
            val eventRecord = SevenKingdomsGameEvent()
            eventRecord.id = generateId()
            eventRecord.gameId = gameId
            eventRecord.index = 0
            eventRecord.userId = userId
            eventRecord.eventType = JOINED
            eventRecord.eventTime = now
            eventRecord.colors = newColorsOfUser.map { it.name }.toTypedArray()
            SevenKingdomsGameEventDao(cfg).insertReactive(eventRecord)

            // update last updated time
            transactional
                .update(SEVEN_KINGDOMS_GAME)
                .set(SEVEN_KINGDOMS_GAME.LAST_UPDATED.fixed(), now)
                .where(SEVEN_KINGDOMS_GAME.ID.eq(gameId))
                .awaitExecute()
        }
    }

    suspend fun resign(
        gameId: String,
        index: Int,
        userId: String,
        colors: List<Color>,
        newStatus: GameEventType?,
        winnerUserId: String?
    ) {
        dslContext.transactionCoroutine { cfg ->
            val transactional by lazy { DSL.using(cfg) }
            val eventDao = SevenKingdomsGameEventDao(cfg)
            val now = Clock.System.now()

            val resignEvent = SevenKingdomsGameEvent()
            resignEvent.id = generateId()
            resignEvent.gameId = gameId
            resignEvent.index = index
            resignEvent.userId = userId
            resignEvent.eventType = RESIGNED
            resignEvent.eventTime = now
            resignEvent.colors = colors.map { it.name }.toTypedArray()
            eventDao.insertReactive(resignEvent)

            if (newStatus != null && winnerUserId != null) {
                val victoryEvent = SevenKingdomsGameEvent()
                victoryEvent.id = generateId()
                victoryEvent.gameId = gameId
                victoryEvent.index = index
                victoryEvent.userId = winnerUserId
                victoryEvent.eventType = OTHER_VICTORY
                victoryEvent.eventTime = now
                victoryEvent.colors = listOf<String>().toTypedArray()
                eventDao.insertReactive(victoryEvent)

                transactional
                    .update(SEVEN_KINGDOMS_GAME)
                    .set(SEVEN_KINGDOMS_GAME.WINNER_USER_ID.fixed(), winnerUserId)
                    .set(SEVEN_KINGDOMS_GAME.GAME_STATUS.fixed(), newStatus)
                    .set(SEVEN_KINGDOMS_GAME.LAST_UPDATED.fixed(), now)
                    .where(SEVEN_KINGDOMS_GAME.ID.eq(gameId))
                    .awaitExecute()
            }
        }
    }

    // TODO: userId un-used?
    suspend fun playMove(
        gameId: String,
        userId: String,
        move: String,
        newIndex: Int,
        newFen: String,
        newColorToPlay: Color?,
        eventType: GameEventType?,
        winnerColor: Color?,
        winnerId: String?
    ) {
        dslContext.transactionCoroutine { cfg ->
            val now = Clock.System.now()
            val transactional = DSL.using(cfg)
            val eventDao by lazy { SevenKingdomsGameEventDao(cfg) }
            val moveDao by lazy { SevenKingdomsGameMoveDao(cfg) }

            val moveRecord = SevenKingdomsGameMove()
            moveRecord.gameId = gameId
            moveRecord.uci = move
            moveRecord.position = newIndex
            moveRecord.moveTime = now
            moveDao.insertReactive(moveRecord)

            transactional
                .update(SEVEN_KINGDOMS_GAME)
                .set(SEVEN_KINGDOMS_GAME.CURRENT_INDEX.fixed(), newIndex)
                .set(SEVEN_KINGDOMS_GAME.CURRENT_FEN.fixed(), newFen)
                .set(SEVEN_KINGDOMS_GAME.COLOR_TO_PLAY.fixed(), newColorToPlay)
                .set(SEVEN_KINGDOMS_GAME.LAST_UPDATED.fixed(), now)
                .where(SEVEN_KINGDOMS_GAME.ID.eq(gameId))
                .awaitExecute()

            if (eventType != null && winnerColor != null && winnerId != null) {
                val eventRecord = SevenKingdomsGameEvent()
                eventRecord.id = generateId()
                eventRecord.gameId = gameId
                eventRecord.index = newIndex
                eventRecord.userId = winnerId
                eventRecord.eventType = eventType
                eventRecord.eventTime = now
                eventRecord.colors = listOf(winnerColor).map { it.name }.toTypedArray()
                eventDao.insertReactive(eventRecord)

                transactional
                    .update(SEVEN_KINGDOMS_GAME)
                    .set(SEVEN_KINGDOMS_GAME.WINNER_COLOR.fixed(), winnerColor)
                    .set(SEVEN_KINGDOMS_GAME.WINNER_USER_ID.fixed(), winnerId)
                    .set(SEVEN_KINGDOMS_GAME.GAME_STATUS.fixed(), eventType)
                    .where(SEVEN_KINGDOMS_GAME.ID.eq(gameId))
                    .awaitExecute()
            }
        }
    }

    companion object {

        fun mapColorToField(color: Color): TableField<SevenKingdomsGameRecord, String> =
            when (color) {
                Color.WHITE -> SEVEN_KINGDOMS_GAME.PLAYER_WHITE
                Color.RED -> SEVEN_KINGDOMS_GAME.PLAYER_RED
                Color.ORANGE -> SEVEN_KINGDOMS_GAME.PLAYER_ORANGE
                Color.BLUE -> SEVEN_KINGDOMS_GAME.PLAYER_BLUE
                Color.GREEN -> SEVEN_KINGDOMS_GAME.PLAYER_GREEN
                Color.PURPLE -> SEVEN_KINGDOMS_GAME.PLAYER_PURPLE
                Color.BLACK -> SEVEN_KINGDOMS_GAME.PLAYER_BLACK
            }

    }

}
