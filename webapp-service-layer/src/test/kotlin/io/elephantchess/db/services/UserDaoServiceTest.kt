package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.dao.codegen.tables.pojos.User
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitSingleMappedRecord
import io.elephantchess.model.TimeControlCategory.BULLET
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.model.UserType.GUEST
import io.elephantchess.xiangqi.Variant.XIANGQI
import io.elephantchess.servicelayer.services.ServiceTest
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserDaoServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val userDaoService by inject<UserDaoService>()

    @Test
    fun `subscribed to all by default`() = runTest {
        val result = signUpTestUser()
        assertSubscribedToAll(result.first.email)
    }

    @Test
    fun `unsubscribeFromAllEmailNotifications with equals email address`() = runTest {
        val result = signUpTestUser()
        val email = result.first.email
        userDaoService.unsubscribeFromAllEmailNotifications(email)
        assertUnsubscribedToAll(email)
    }

    @Test
    fun `unsubscribeFromAllEmailNotifications with equals ignore case`() = runTest {
        val result = signUpTestUser()
        val email = result.first.email
        userDaoService.unsubscribeFromAllEmailNotifications(email.uppercase())
        assertUnsubscribedToAll(email)
    }

    @Test
    fun `unsubscribeFromAllEmailNotifications with equals with additional with spaces`() = runTest {
        val result = signUpTestUser()
        val email = result.first.email
        userDaoService.unsubscribeFromAllEmailNotifications("  ${email.uppercase()} ")
        assertUnsubscribedToAll(email)
    }

    @Test
    fun `fetchRatingSummary supports guest and authenticated filters`() = runTest {
        val baseline = userDaoService.fetchRatingSummary(BULLET, XIANGQI)
        val (_, authenticatedUserId) = signUpTestUser(2_001)
        val guestUserId = userDaoService.createGuestUser()

        dslContext
            .update(USER)
            .set(USER.GAME_RATING_BULLET, 5_000)
            .where(USER.ID.eq(authenticatedUserId))
            .awaitExecute()

        dslContext
            .update(USER)
            .set(USER.GAME_RATING_BULLET, 900)
            .where(USER.ID.eq(guestUserId))
            .awaitExecute()

        val result = userDaoService.fetchRatingSummary(BULLET, XIANGQI)
        val authenticatedResult = userDaoService.fetchRatingSummary(BULLET, XIANGQI, AUTHENTICATED)
        val guestResult = userDaoService.fetchRatingSummary(BULLET, XIANGQI, GUEST)

        assertEquals(baseline.userCount + 2, result.userCount)
        assertEquals(900, result.minRating)
        assertEquals(guestUserId, result.minUserId)
        assertEquals(5_000, result.maxRating)
        assertEquals(authenticatedUserId, result.maxUserId)
        assertEquals(5_000, authenticatedResult.maxRating)
        assertEquals(authenticatedUserId, authenticatedResult.maxUserId)
        assertEquals(900, guestResult.minRating)
        assertEquals(guestUserId, guestResult.minUserId)
    }

    private suspend fun assertUnsubscribedToAll(email: String) {
        val user = dslContext.selectFrom(USER)
            .where(USER.EMAIL.eq(email))
            .awaitSingleMappedRecord<User>()!!

        assertFalse(user.emailNotificationEnabledNewsletter)
        assertFalse(user.emailNotificationEnabledUserJoinedGame)
        assertFalse(user.emailNotificationEnabledUserFlagged)
        assertFalse(user.emailNotificationEnabledOpponentFlagged)
        assertFalse(user.emailNotificationEnabledOpponentPlayedMove)
        assertFalse(user.emailNotificationEnabledOpponentResigned)
        assertFalse(user.emailNotificationEnabledOpponentProposedDraw)
        assertFalse(user.emailNotificationEnabledOpponentAcceptedDraw)
        assertFalse(user.emailNotificationEnabledOpponentDeclinedDraw)
    }

    private suspend fun assertSubscribedToAll(email: String) {
        val user = dslContext.selectFrom(USER)
            .where(USER.EMAIL.eq(email))
            .awaitSingleMappedRecord<User>()!!

        assertTrue(user.emailNotificationEnabledNewsletter)
        assertTrue(user.emailNotificationEnabledUserJoinedGame)
        assertTrue(user.emailNotificationEnabledUserFlagged)
        assertTrue(user.emailNotificationEnabledOpponentFlagged)
        assertTrue(user.emailNotificationEnabledOpponentPlayedMove)
        assertTrue(user.emailNotificationEnabledOpponentResigned)
        assertTrue(user.emailNotificationEnabledOpponentProposedDraw)
        assertTrue(user.emailNotificationEnabledOpponentAcceptedDraw)
        assertTrue(user.emailNotificationEnabledOpponentDeclinedDraw)
    }

}
