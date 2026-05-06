package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME_EVENT
import io.elephantchess.db.dao.codegen.tables.pojos.ReferenceGameEvent
import io.elephantchess.db.model.EventListEntryRecord
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.awaitSingleMappedRecord
import io.elephantchess.db.utils.awaitSingleValue
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.LocalDate

class ReferenceEventDaoService(private val dslContext: DSLContext) {

    suspend fun fetchEventById(eventId: String): ReferenceGameEvent? {
        return dslContext
            .select()
            .from(REFERENCE_GAME_EVENT)
            .where(REFERENCE_GAME_EVENT.ID.eq(eventId))
            .awaitSingleMappedRecord()
    }

    suspend fun findEventName(id: String): String? {
        return dslContext
            .select(REFERENCE_GAME_EVENT.NAME)
            .from(REFERENCE_GAME_EVENT)
            .where(REFERENCE_GAME_EVENT.ID.eq(id))
            .and(REFERENCE_GAME_EVENT.IS_VISIBLE.eq(true))
            .awaitSingleValue()
    }

    suspend fun listAllEventsWithStats(
        limit: Int? = null,
        offset: Int? = null
    ): List<EventListEntryRecord> {
        val baseQuery = dslContext
            .select(
                REFERENCE_GAME_EVENT.ID,
                REFERENCE_GAME_EVENT.NAME,
                DSL.max(REFERENCE_GAME.DATE).`as`("latest_date"),
                DSL.max(REFERENCE_GAME.ROUND).`as`("max_round"),
                DSL.count().`as`("game_count")
            )
            .from(REFERENCE_GAME, REFERENCE_GAME_EVENT)
            .where(REFERENCE_GAME.EVENT.eq(REFERENCE_GAME_EVENT.ID))
            .and(REFERENCE_GAME_EVENT.IS_VISIBLE.eq(true))
            .groupBy(REFERENCE_GAME_EVENT.ID, REFERENCE_GAME_EVENT.NAME)
            .orderBy(DSL.field("latest_date").desc().nullsLast())
            .run {
                when {
                    limit != null && offset != null -> this.limit(limit).offset(offset)
                    limit != null -> this.limit(limit)
                    offset != null -> this.offset(offset)
                    else -> this
                }
            }

        return baseQuery
            .awaitRecords()
            .map { record ->
                EventListEntryRecord(
                    id = record.get(REFERENCE_GAME_EVENT.ID),
                    name = record.get(REFERENCE_GAME_EVENT.NAME),
                    date = record.get("latest_date", LocalDate::class.java),
                    maxRound = record.get("max_round", Int::class.java),
                    gameCount = record.get("game_count", Int::class.java) ?: 0
                )
            }
    }

}
