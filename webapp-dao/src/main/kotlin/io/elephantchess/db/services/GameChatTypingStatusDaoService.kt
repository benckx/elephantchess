package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.GAME_CHAT_TYPING_STATUS
import io.elephantchess.db.dao.codegen.tables.pojos.GameChatTypingStatus
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.fixed
import io.elephantchess.db.utils.isAfter
import org.jooq.DSLContext
import org.jooq.impl.DSL
import kotlin.time.Clock
import kotlin.time.Instant

class GameChatTypingStatusDaoService(private val dslContext: DSLContext) {

    suspend fun upsertTypingStatus(gameId: String, userId: String) {
        val now = Clock.System.now()

        // insert, or update on conflict
        dslContext
            .insertInto(GAME_CHAT_TYPING_STATUS.fixed())
            .set(GAME_CHAT_TYPING_STATUS.GAME_ID.fixed(), gameId)
            .set(GAME_CHAT_TYPING_STATUS.USER_ID.fixed(), userId)
            .set(GAME_CHAT_TYPING_STATUS.TYPED_AT.fixed(), now)
            .onConflict(GAME_CHAT_TYPING_STATUS.GAME_ID, GAME_CHAT_TYPING_STATUS.USER_ID)
            .doUpdate()
            .set(GAME_CHAT_TYPING_STATUS.TYPED_AT, DSL.excluded(GAME_CHAT_TYPING_STATUS.TYPED_AT))
            .awaitExecute()
    }

    /**
     * Returns a map keyed by gameId where each value is the list of [GameChatTypingStatus]
     * for that game. Only entries newer than [cutOff] (i.e. typed after that
     * instant) are returned, so stale typing events never leak to callers.
     */
    suspend fun fetchForGameIds(
        gameIds: List<String>,
        cutOff: Instant,
    ): Map<String, List<GameChatTypingStatus>> {
        if (gameIds.isEmpty()) return emptyMap()

        return dslContext
            .selectFrom(GAME_CHAT_TYPING_STATUS)
            .where(GAME_CHAT_TYPING_STATUS.GAME_ID.`in`(gameIds))
            .and(GAME_CHAT_TYPING_STATUS.TYPED_AT.isAfter(cutOff))
            .awaitMappedRecords<GameChatTypingStatus>()
            .groupBy { it.gameId!! }
    }

}
