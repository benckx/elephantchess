package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.PAGE_VIEW_EVENT
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME_SEARCH_QUERY
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.dao.codegen.Tables.USER_SESSION
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.services.ServiceTest
import kotlinx.coroutines.test.runTest
import org.apache.commons.lang3.RandomStringUtils.insecure
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class UserDaoServiceAccountDeletionTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val userDaoService by inject<UserDaoService>()

    @Test
    fun `deletes the account and transfers data to a new anonymized user`() =
        runTest {
            val userId = createAuthenticatedUser(puzzleRating = 1234)
            insertPageView(userId)
            insertSession(userId)
            insertSearchQuery(userId)

            val anonymizedId = userDaoService.deleteAndAnonymize(userId)

            assertNotNull(anonymizedId)
            assertNotEquals(userId, anonymizedId)

            // original account is gone, together with its sessions and raw page views
            assertFalse(userExists(userId))
            assertEquals(0, countSessions(userId))
            assertEquals(0, countPageViews(userId))

            // the new user is anonymized, keeps the ratings and holds no personal data
            val anonymized = userDaoService.findById(anonymizedId)
            assertNotNull(anonymized)
            assertEquals(UserType.ANONYMIZED, anonymized.userType)
            assertEquals(1234, anonymized.puzzleRating)
            assertNull(anonymized.email)
            assertNull(anonymized.handle)
            assertNull(anonymized.password)

            // referencing data (here a search query) is transferred to the anonymized user
            assertEquals(0, countSearchQueries(userId))
            assertEquals(1, countSearchQueries(anonymizedId))
        }

    @Test
    fun `returns null and changes nothing for an unknown user`() =
        runTest {
            val result = userDaoService.deleteAndAnonymize("does-not-exist")
            assertNull(result)
        }

    private suspend fun createAuthenticatedUser(puzzleRating: Int): String {
        val id = insecure().nextAlphanumeric(12)
        val now = Clock.System.now()
        dslContext
            .insertInto(USER)
            .set(USER.ID, id)
            .set(USER.HANDLE, "user_${insecure().nextAlphanumeric(6)}")
            .set(USER.EMAIL, "${insecure().nextAlphanumeric(8)}@example.com")
            .set(USER.PASSWORD, byteArrayOf(1, 2, 3))
            .set(USER.CREATION, now)
            .set(USER.LAST_ONLINE, now)
            .set(USER.USER_TYPE, UserType.AUTHENTICATED)
            .set(USER.EMAIL_NOTIFICATION_ENABLED_NEWSLETTER, false)
            .set(USER.EMAIL_NOTIFICATION_ENABLED_USER_JOINED_GAME, false)
            .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PLAYED_MOVE, false)
            .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_RESIGNED, false)
            .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_PROPOSED_DRAW, false)
            .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_ACCEPTED_DRAW, false)
            .set(USER.EMAIL_NOTIFICATION_ENABLED_OPPONENT_DECLINED_DRAW, false)
            .set(USER.PUZZLE_RATING, puzzleRating)
            .awaitExecute()
        return id
    }

    private suspend fun insertPageView(userId: String) {
        dslContext
            .insertInto(PAGE_VIEW_EVENT)
            .set(PAGE_VIEW_EVENT.EVENT_ID, insecure().nextAlphanumeric(12))
            .set(PAGE_VIEW_EVENT.USER_ID, userId)
            .set(PAGE_VIEW_EVENT.EVENT_TIME, Clock.System.now())
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

    private suspend fun insertSearchQuery(userId: String) {
        val now = Clock.System.now()
        dslContext
            .insertInto(REFERENCE_GAME_SEARCH_QUERY)
            .set(REFERENCE_GAME_SEARCH_QUERY.QUERY_ID, insecure().nextAlphanumeric(12))
            .set(REFERENCE_GAME_SEARCH_QUERY.USER_ID, userId)
            .set(REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME, now)
            .set(REFERENCE_GAME_SEARCH_QUERY.UPDATE_TIME, now)
            .set(REFERENCE_GAME_SEARCH_QUERY.NUMBER_OF_RESULTS, 0)
            .set(REFERENCE_GAME_SEARCH_QUERY.LIMIT, 10)
            .awaitExecute()
    }

    private suspend fun userExists(userId: String): Boolean =
        dslContext
            .selectCount()
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleValue<Int>()!! > 0

    private suspend fun countPageViews(userId: String): Int =
        dslContext
            .selectCount()
            .from(PAGE_VIEW_EVENT)
            .where(PAGE_VIEW_EVENT.USER_ID.eq(userId))
            .awaitSingleValue()!!

    private suspend fun countSessions(userId: String): Int =
        dslContext
            .selectCount()
            .from(USER_SESSION)
            .where(USER_SESSION.USER_ID.eq(userId))
            .awaitSingleValue()!!

    private suspend fun countSearchQueries(userId: String): Int =
        dslContext
            .selectCount()
            .from(REFERENCE_GAME_SEARCH_QUERY)
            .where(REFERENCE_GAME_SEARCH_QUERY.USER_ID.eq(userId))
            .awaitSingleValue()!!
}
