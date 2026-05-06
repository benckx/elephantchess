package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.UPCOMING_EVENT
import io.elephantchess.db.dao.codegen.tables.daos.UpcomingEventDao
import io.elephantchess.db.dao.codegen.tables.pojos.UpcomingEvent
import io.elephantchess.db.utils.*
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.time.LocalDate

class UpcomingEventDaoService(private val dslContext: DSLContext) {

    suspend fun listUpcomingEventsForLobby(): List<UpcomingEvent> {
        val today = LocalDate.now()

        return dslContext
            .select()
            .from(UPCOMING_EVENT)
            .where(UPCOMING_EVENT.EVENT_END.greaterOrEqual(today))
            .and(UPCOMING_EVENT.IS_ENABLED.eq(true))
            .orderBy(UPCOMING_EVENT.EVENT_START.asc())
            .awaitMappedRecords()
    }

    suspend fun listAllUpcomingEvents(): List<UpcomingEvent> {
        return dslContext
            .select()
            .from(UPCOMING_EVENT)
            .orderBy(UPCOMING_EVENT.EVENT_START.desc())
            .awaitMappedRecords()
    }

    suspend fun save(event: UpcomingEvent): Int {
        var newId = 0

        dslContext.transactionCoroutine { cfg ->
            val transactional = DSL.using(cfg)

            val maxId = transactional
                .select(DSL.max(UPCOMING_EVENT.ID))
                .from(UPCOMING_EVENT)
                .awaitSingleValue() ?: 0

            newId = maxId + 1
            event.id = newId
            UpcomingEventDao(cfg).insertReactive(event)
        }

        return newId
    }

    suspend fun updateEvent(event: UpcomingEvent) {
        dslContext
            .update(UPCOMING_EVENT)
            .set(UPCOMING_EVENT.EVENT_START, event.eventStart)
            .set(UPCOMING_EVENT.EVENT_END, event.eventEnd)
            .set(UPCOMING_EVENT.DESCRIPTION, event.description)
            .set(UPCOMING_EVENT.LINK, event.link)
            .where(UPCOMING_EVENT.ID.eq(event.id))
            .awaitExecute()
    }

    suspend fun toggleEnabled(id: Int, enabled: Boolean) {
        dslContext
            .update(UPCOMING_EVENT)
            .set(UPCOMING_EVENT.IS_ENABLED, enabled)
            .where(UPCOMING_EVENT.ID.eq(id))
            .awaitExecute()
    }

    suspend fun findById(eventId: Int): UpcomingEvent? {
        return dslContext
            .select()
            .from(UPCOMING_EVENT)
            .where(UPCOMING_EVENT.ID.eq(eventId))
            .awaitSingleMappedRecord()
    }

}
