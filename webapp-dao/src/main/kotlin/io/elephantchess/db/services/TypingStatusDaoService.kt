package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.GAME_TYPING_STATUS
import io.elephantchess.db.dao.codegen.tables.pojos.GameTypingStatus
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.fixed
import io.elephantchess.db.utils.isBefore
import io.elephantchess.db.utils.isBeforeEpochMillis
import io.elephantchess.db.utils.isWithin
import org.jooq.DSLContext
import org.jooq.impl.DSL
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

data class TypingStatusEntry(val userId: String, val typedAt: Instant)

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
     * Returns a map keyed by gameId where each value is the list of [TypingStatusEntry]
     * for that game. Only entries for the requested [gameIds] are returned.
     */
    suspend fun fetchTypingStatuses(gameIds: List<String>, limit : Instant): Map<String, List<TypingStatusEntry>> {
        if (gameIds.isEmpty()) return emptyMap()

        return dslContext
            .selectFrom(GAME_TYPING_STATUS)
            .where(GAME_TYPING_STATUS.GAME_ID.`in`(gameIds))
            .and(GAME_TYPING_STATUS.TYPED_AT.isBefore(limit))
            .awaitMappedRecords<GameTypingStatus>()
            .groupBy { it.gameId!! }
            .mapValues { (_, records) ->
                records.map { TypingStatusEntry(userId = it.userId!!, typedAt = it.typedAt!!) }
            }
    }

}
