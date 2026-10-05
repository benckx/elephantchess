package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.ARCHIVED_PAGE_VIEW_DAILY
import io.elephantchess.db.dao.codegen.Tables.PAGE_VIEW_EVENT
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.db.utils.instantOfUtc
import io.elephantchess.servicelayer.services.ServiceTest
import kotlinx.coroutines.test.runTest
import org.apache.commons.lang3.RandomStringUtils.insecure
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ArchivedPageViewDaoServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val archivedPageViewDaoService by inject<ArchivedPageViewDaoService>()

    @Test
    fun `archives old page views per url and deletes the events`() = runTest {
        val (_, userId) = signUpTestUser()

        // two views on the same day+url collapse to one unique daily view; a third on another day
        insertPageView(userId, instantOfUtc(2020, 3, 10, 12, 1, 0), eventPath = "/database")
        insertPageView(userId, instantOfUtc(2020, 3, 10, 12, 2, 0), eventPath = "/database")
        insertPageView(userId, instantOfUtc(2020, 3, 9, 8, 0, 0), eventPath = "/puzzles")

        val archived = archiveOldPageViews(maxAge = 90.days)

        assertEquals(3, archived)
        assertEquals(0, countPageViews(userId))
        assertEquals(1, archivedPageViews(LocalDate.of(2020, 3, 10), "/database"))
        assertEquals(1, archivedPageViews(LocalDate.of(2020, 3, 9), "/puzzles"))
    }

    @Test
    fun `does not archive recent page views`() = runTest {
        val (_, userId) = signUpTestUser()
        insertPageView(userId, Clock.System.now(), eventPath = "/database")

        val archived = archiveOldPageViews(maxAge = 90.days)

        assertEquals(0, archived)
        assertEquals(1, countPageViews(userId))
    }

    @Test
    fun `archives all page views for a user regardless of age`() = runTest {
        val (_, userId) = signUpTestUser()
        insertPageView(userId, Clock.System.now(), eventPath = "/database")
        insertPageView(userId, instantOfUtc(2020, 3, 10, 12, 1, 0), eventPath = "/puzzles")

        assertEquals(2, archivedPageViewDaoService.countPageViewsForUser(userId))

        val archived = archivedPageViewDaoService.archiveAndDeletePageViewsForUser(userId)

        assertEquals(2, archived)
        assertEquals(0, archivedPageViewDaoService.countPageViewsForUser(userId))
        assertEquals(1, archivedPageViews(LocalDate.of(2020, 3, 10), "/puzzles"))
    }

    @Test
    fun `lists most recent page views first`() = runTest {
        val (_, userId) = signUpTestUser()
        insertPageView(userId, instantOfUtc(2020, 3, 9, 8, 0, 0), eventPath = "/old")
        insertPageView(userId, instantOfUtc(2020, 3, 10, 12, 0, 0), eventPath = "/new")

        val views = archivedPageViewDaoService.listPageViewsForUser(userId, limit = 10, offset = 0)

        assertEquals(2, views.size)
        assertEquals("/new", views[0].url)
        assertEquals("/old", views[1].url)
    }

    @Test
    fun `archives own and other profile page views into their buckets`() = runTest {
        val (request, userId) = signUpTestUser()
        val handle = request.username

        insertPageView(userId, instantOfUtc(2021, 7, 12, 12, 1, 0), eventPath = "/@/$handle")
        insertPageView(userId, instantOfUtc(2021, 7, 12, 12, 2, 0), eventPath = "/@/someoneelse")
        // a profile sub-path is not a profile view and must be excluded from both buckets
        insertPageView(userId, instantOfUtc(2021, 7, 12, 12, 3, 0), eventPath = "/@/$handle/games")

        archiveOldPageViews(maxAge = 90.days)

        assertEquals(1, archivedBucket(LocalDate.of(2021, 7, 12), "/@/$handle", ARCHIVED_PAGE_VIEW_DAILY.OWN_PROFILE_PAGE_VIEWS))
        assertEquals(1, archivedBucket(LocalDate.of(2021, 7, 12), "/@/someoneelse", ARCHIVED_PAGE_VIEW_DAILY.OTHER_PROFILE_PAGE_VIEWS))
    }

    private suspend fun archiveOldPageViews(maxAge: kotlin.time.Duration): Int {
        val eventIds = archivedPageViewDaoService.selectOldPageViewEventIds(maxAge, limit = 1_000)
        return archivedPageViewDaoService.archiveAndDeletePageViews(eventIds)
    }

    private suspend fun insertPageView(userId: String, eventTime: Instant, eventPath: String = "/") {
        dslContext
            .insertInto(PAGE_VIEW_EVENT)
            .set(PAGE_VIEW_EVENT.EVENT_ID, insecure().nextAlphanumeric(12))
            .set(PAGE_VIEW_EVENT.USER_ID, userId)
            .set(PAGE_VIEW_EVENT.EVENT_TIME, eventTime)
            .set(PAGE_VIEW_EVENT.EVENT_PATH, eventPath)
            .awaitExecute()
    }

    private suspend fun countPageViews(userId: String): Int =
        dslContext
            .selectCount()
            .from(PAGE_VIEW_EVENT)
            .where(PAGE_VIEW_EVENT.USER_ID.eq(userId))
            .awaitSingleValue()!!

    private suspend fun archivedPageViews(day: LocalDate, url: String): Int =
        dslContext
            .select(ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS)
            .from(ARCHIVED_PAGE_VIEW_DAILY)
            .where(
                ARCHIVED_PAGE_VIEW_DAILY.DAY.eq(day)
                    .and(ARCHIVED_PAGE_VIEW_DAILY.URL.eq(url))
            )
            .awaitSingleValue() ?: 0

    private suspend fun archivedBucket(day: LocalDate, url: String, field: org.jooq.TableField<*, Int>): Int =
        dslContext
            .select(field)
            .from(ARCHIVED_PAGE_VIEW_DAILY)
            .where(
                ARCHIVED_PAGE_VIEW_DAILY.DAY.eq(day)
                    .and(ARCHIVED_PAGE_VIEW_DAILY.URL.eq(url))
            )
            .awaitSingleValue() ?: 0

}
