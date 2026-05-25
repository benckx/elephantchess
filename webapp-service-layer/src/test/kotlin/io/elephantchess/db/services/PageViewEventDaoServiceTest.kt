package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.PAGE_VIEW_EVENT
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.dao.codegen.Tables.USER_SESSION
import io.elephantchess.db.dao.codegen.tables.pojos.PageViewEvent
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.generateId
import io.elephantchess.servicelayer.services.ServiceTest
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PageViewEventDaoServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val pageViewEventDaoService by inject<PageViewEventDaoService>()

    @AfterTest
    fun afterEach() = runTest {
        dslContext.deleteFrom(PAGE_VIEW_EVENT).awaitExecute()
        dslContext.deleteFrom(USER_SESSION).awaitExecute()
        dslContext.deleteFrom(USER).awaitExecute()
    }

    @Test
    fun `fetchMonthlyUserProfilePageViews returns aggregated profile views`() = runTest {
        val users = (1..5).map { signUpTestUser().second }

        savePageView(users[0], "/@/alice")
        savePageView(users[1], "/@/bob?medium=footer")
        savePageView(users[2], "/@/charlie?gad_source=1")
        savePageView(users[3], "/@/david/browse-pvp-games")
        savePageView(users[4], "/about")

        val records = pageViewEventDaoService.fetchMonthlyUserProfilePageViews(excludedUserIds = emptyList())

        assertEquals(1, records.size)
        assertEquals("/@/{username}", records.first().label)
        assertEquals(3, records.first().uniquePageViews)
    }

    @Test
    fun `fetchMonthlyUserProfilePageViews excludes requested users`() = runTest {
        val users = (1..3).map { signUpTestUser().second }

        savePageView(users[0], "/@/alice")
        savePageView(users[1], "/@/bob")
        savePageView(users[2], "/@/charlie")

        val records = pageViewEventDaoService.fetchMonthlyUserProfilePageViews(
            excludedUserIds = listOf(users[1])
        )

        assertEquals(1, records.size)
        assertEquals(2, records.first().uniquePageViews)
    }

    private suspend fun savePageView(userId: String, path: String) {
        val event = PageViewEvent()
        event.eventId = generateId()
        event.userId = userId
        event.eventPath = path
        pageViewEventDaoService.save(event)
    }
}
