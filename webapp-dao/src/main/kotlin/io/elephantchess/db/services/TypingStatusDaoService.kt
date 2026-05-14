package io.elephantchess.db.services

import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitRecords
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

class TypingStatusDaoService(private val dslContext: DSLContext) {

    private val table = DSL.table("game_typing_status")
    private val gameIdField = DSL.field("game_id", String::class.java)
    private val userIdField = DSL.field("user_id", String::class.java)
    private val typedAtField = DSL.field("typed_at", OffsetDateTime::class.java)

    suspend fun upsertTypingStatus(gameId: String, userId: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dslContext
            .insertInto(table)
            .columns(gameIdField, userIdField, typedAtField)
            .values(gameId, userId, now)
            .onConflict(gameIdField, userIdField)
            .doUpdate()
            .set(typedAtField, DSL.excluded(typedAtField))
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
            .select(gameIdField, userIdField, typedAtField)
            .from(table)
            .where(gameIdField.`in`(gameIds))
            .awaitRecords()
            .groupBy { record -> record.get(gameIdField)!! }
            .mapValues { (_, records) ->
                records.associate { record ->
                    record.get(userIdField)!! to
                            record.get(typedAtField)!!.toInstant().toKotlinInstant()
                }
            }
    }

}
