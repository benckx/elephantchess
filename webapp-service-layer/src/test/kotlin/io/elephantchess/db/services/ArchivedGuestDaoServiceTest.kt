package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.ARCHIVED_GUEST_DAILY
import io.elephantchess.db.dao.codegen.Tables.ARCHIVED_PAGE_VIEW_DAILY
import io.elephantchess.db.dao.codegen.Tables.ANALYSIS
import io.elephantchess.db.dao.codegen.Tables.PAGE_VIEW_EVENT
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME_SEARCH_QUERY
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.dao.codegen.Tables.USER_SESSION
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.db.utils.instantOfUtc
import io.elephantchess.servicelayer.services.ServiceTest
import io.elephantchess.servicelayer.services.analytics.allMetrics
import kotlinx.coroutines.test.runTest
import org.apache.commons.lang3.RandomStringUtils.insecure
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ArchivedGuestDaoServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val userDaoService by inject<UserDaoService>()
    private val archivedGuestDaoService by inject<ArchivedGuestDaoService>()

    @Test
    fun `archives old inactive guest and deletes its data`() = runTest {
        val creationDay = LocalDate.of(2020, 3, 10)
        val creation = instantOfUtc(2020, 3, 10, 12, 0, 0)

        // lifespan of 20 minutes -> "under 30 min" bucket
        val guestId = createOldGuest(creation = creation, lifespanSeconds = 20 * 60)

        insertPageView(guestId, instantOfUtc(2020, 3, 10, 12, 1, 0))
        insertPageView(guestId, instantOfUtc(2020, 3, 10, 12, 2, 0))
        insertPageView(guestId, instantOfUtc(2020, 3, 9, 8, 0, 0))
        insertSession(guestId)
        insertSearchQuery(guestId, instantOfUtc(2020, 3, 10, 12, 3, 0))
        insertSearchQuery(guestId, instantOfUtc(2020, 3, 10, 12, 4, 0))

        val under30MinBefore = archivedGuestBucket(creationDay, ARCHIVED_GUEST_DAILY.GUESTS_UNDER_30MIN)

        val result = archivedGuestDaoService.archiveAndDeleteOldGuests(maxAge = 90.days, batchSize = 1_000)

        assertTrue(result.archivedGuests >= 1)
        assertFalse(userExists(guestId))
        assertEquals(0, countPageViews(guestId))
        assertEquals(0, countSessions(guestId))

        // guest counted once, in the under-30-min bucket, on its creation day
        assertEquals(
            under30MinBefore + 1,
            archivedGuestBucket(creationDay, ARCHIVED_GUEST_DAILY.GUESTS_UNDER_30MIN),
        )

        // two views on 2020-03-10 collapse to one unique daily view; one on 2020-03-09
        assertEquals(1, archivedPageViews(LocalDate.of(2020, 3, 10)))
        assertEquals(1, archivedPageViews(LocalDate.of(2020, 3, 9)))

        // both searches happened on 2020-03-10 and are archived on that day
        assertEquals(2, archivedGuestBucket(creationDay, ARCHIVED_GUEST_DAILY.DELETED_SEARCHES))
    }

    @Test
    fun `does not archive recent guest`() = runTest {
        val guestId = createOldGuest(creation = Clock.System.now(), lifespanSeconds = 20 * 60)

        archivedGuestDaoService.archiveAndDeleteOldGuests(maxAge = 90.days, batchSize = 1_000)

        assertTrue(userExists(guestId))
    }

    @Test
    fun `does not archive old guest that owns an analysis`() = runTest {
        val creation = instantOfUtc(2019, 1, 5, 12, 0, 0)
        val guestId = createOldGuest(creation = creation, lifespanSeconds = 20 * 60)

        dslContext
            .insertInto(ANALYSIS)
            .set(ANALYSIS.ID, insecure().nextAlphanumeric(12))
            .set(ANALYSIS.OWNER_USER_ID, guestId)
            .set(ANALYSIS.ANALYSIS_NAME, "test analysis")
            .set(ANALYSIS.CREATED, creation)
            .set(ANALYSIS.LAST_UPDATED, creation)
            .awaitExecute()

        archivedGuestDaoService.archiveAndDeleteOldGuests(maxAge = 90.days, batchSize = 1_000)

        assertTrue(userExists(guestId))
    }

    @Test
    fun `archived guest feeds the archived guests metric`() = runTest {
        val creation = instantOfUtc(2018, 6, 20, 12, 0, 0)
        // 20 minutes -> counts as an "archived guest" (>= 15 min)
        createOldGuest(creation = creation, lifespanSeconds = 20 * 60)

        archivedGuestDaoService.archiveAndDeleteOldGuests(maxAge = 90.days, batchSize = 1_000)

        val archivedGuests = metricValueForYear("archived guests", 2018)
        // the guest was deleted, so the live-only "new guests" metric no longer counts it
        val newGuests = metricValueForYear("new guests", 2018)

        assertEquals(1, archivedGuests)
        assertEquals(0, newGuests)
    }

    private suspend fun metricValueForYear(metricName: String, year: Int): Int {
        val metric = allMetrics.first { it.name == metricName }
        return metric.countByYear(dslContext)
            .firstOrNull { it.period == year }
            ?.value
            ?.toInt()
            ?: 0
    }

    private suspend fun createOldGuest(creation: Instant, lifespanSeconds: Int): String {
        val guestId = userDaoService.createGuestUser()
        dslContext
            .update(USER)
            .set(USER.CREATION, creation)
            .set(USER.LAST_ONLINE, creation + lifespanSeconds.seconds)
            .where(USER.ID.eq(guestId))
            .awaitExecute()
        return guestId
    }

    private suspend fun insertPageView(userId: String, eventTime: Instant) {
        dslContext
            .insertInto(PAGE_VIEW_EVENT)
            .set(PAGE_VIEW_EVENT.EVENT_ID, insecure().nextAlphanumeric(12))
            .set(PAGE_VIEW_EVENT.USER_ID, userId)
            .set(PAGE_VIEW_EVENT.EVENT_TIME, eventTime)
            .set(PAGE_VIEW_EVENT.EVENT_PATH, "/")
            .awaitExecute()
    }

    private suspend fun insertSession(userId: String) {
        val now = Clock.System.now()
        dslContext
            .insertInto(USER_SESSION)
            .set(USER_SESSION.USER_ID, userId)
            .set(USER_SESSION.REMOTE_ADDRESS, "127.0.0.1")
            .set(USER_SESSION.USER_AGENT, "test-agent")
            .set(USER_SESSION.OPERATING_SYSTEM_NAME, "test-os")
            .set(USER_SESSION.AGENT_NAME, "test")
            .set(USER_SESSION.AGENT_CLASS, "test")
            .set(USER_SESSION.CREATED, now)
            .set(USER_SESSION.LAST_UPDATED, now)
            .awaitExecute()
    }

    private suspend fun insertSearchQuery(userId: String, queryTime: Instant) {
        dslContext
            .insertInto(REFERENCE_GAME_SEARCH_QUERY)
            .set(REFERENCE_GAME_SEARCH_QUERY.QUERY_ID, insecure().nextAlphanumeric(12))
            .set(REFERENCE_GAME_SEARCH_QUERY.USER_ID, userId)
            .set(REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME, queryTime)
            .set(REFERENCE_GAME_SEARCH_QUERY.UPDATE_TIME, queryTime)
            .set(REFERENCE_GAME_SEARCH_QUERY.NUMBER_OF_RESULTS, 0)
            .set(REFERENCE_GAME_SEARCH_QUERY.LIMIT, 10)
            .awaitExecute()
    }

    private suspend fun userExists(userId: String): Boolean =
        dslContext.selectCount().from(USER).where(USER.ID.eq(userId)).awaitSingleValue<Int>()!! > 0

    private suspend fun countPageViews(userId: String): Int =
        dslContext.selectCount().from(PAGE_VIEW_EVENT).where(PAGE_VIEW_EVENT.USER_ID.eq(userId))
            .awaitSingleValue<Int>()!!

    private suspend fun countSessions(userId: String): Int =
        dslContext.selectCount().from(USER_SESSION).where(USER_SESSION.USER_ID.eq(userId))
            .awaitSingleValue<Int>()!!

    private suspend fun archivedGuestBucket(day: LocalDate, field: org.jooq.TableField<*, Int>): Int =
        dslContext.select(field).from(ARCHIVED_GUEST_DAILY).where(ARCHIVED_GUEST_DAILY.DAY.eq(day))
            .awaitSingleValue<Int>() ?: 0

    private suspend fun archivedPageViews(day: LocalDate): Int =
        dslContext.select(ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS).from(ARCHIVED_PAGE_VIEW_DAILY)
            .where(ARCHIVED_PAGE_VIEW_DAILY.DAY.eq(day)).awaitSingleValue<Int>() ?: 0

}
