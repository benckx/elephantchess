package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.GAME_TYPING_STATUS
import io.elephantchess.db.dao.codegen.tables.pojos.GameTypingStatus
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.fixed
import org.jooq.DSLContext
import org.jooq.impl.DSL
import kotlin.time.Clock
import kotlin.time.Instant

class TypingStatusDaoService(private val dslContext: DSLContext) {

    suspend fun upsertTypingStatus(gameId: String, userId: String) {
        val now = Clock.System.now()
        dslContext
            .insertInto(GAME_TYPING_STATUS.fixed())
            .set(GAME_TYPING_STATUS.GAME_ID.fixed(), gameId)
            .set(GAME_TYPING_STATUS.USER_ID.fixed(), userId)
            .set(GAME_TYPING_STATUS.TYPED_AT.fixed(), now)
            .onConflict(GAME_TYPING_STATUS.GAME_ID, GAME_TYPING_STATUS.USER_ID)
            .doUpdate()
            .set(GAME_TYPING_STATUS.TYPED_AT, DSL.excluded(GAME_TYPING_STATUS.TYPED_AT))
            .awaitExecute()
    }

    /**
     * Returns a nested map where the outer key is gameId and each inner map is keyed by userId
     * with values representing the last typed-at [Instant] for that user in that game.
     * Only entries for the requested [gameIds] are returned.
     */
    suspend fun fetchTypingStatuses(gameIds: List<String>): Map<String, Map<String, Instant>> {
        if (gameIds.isEmpty()) return emptyMap()

        return dslContext
            .selectFrom(GAME_TYPING_STATUS)
            .where(GAME_TYPING_STATUS.GAME_ID.`in`(gameIds))
            .awaitMappedRecords<GameTypingStatus>()
            .groupBy { it.gameId!! }
            .mapValues { (_, records) ->
                records.associate { it.userId!! to it.typedAt!! }
            }
    }

}
