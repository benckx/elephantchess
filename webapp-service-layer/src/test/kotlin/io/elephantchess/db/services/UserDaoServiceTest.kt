package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.dao.codegen.tables.pojos.User
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitSingleMappedRecord
import io.elephantchess.model.GameEventType.CHECKMATED
import io.elephantchess.model.GameEventType.DRAW_ACCEPTED
import io.elephantchess.model.Outcome
import io.elephantchess.model.TimeControlCategory
import io.elephantchess.model.TimeControlMode
import io.elephantchess.servicelayer.services.ServiceTest
import io.elephantchess.xiangqi.Color
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.time.Clock
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
    fun `fetchPlayerVsPlayerOutcomeStats should count wins losses and draws`() = runTest {
        val userId = signUpTestUser().second
        val opponentId = signUpTestUser().second
        val otherUserId = signUpTestUser().second
        val now = Clock.System.now()

        suspend fun insertGame(
            id: String,
            inviter: String,
            invitee: String,
            inviterColor: Color,
            outcome: Outcome,
        ) {
            val gameStatus = if (outcome == Outcome.DRAW) DRAW_ACCEPTED else CHECKMATED
            dslContext
                .insertInto(GAME)
                .set(GAME.ID, id)
                .set(GAME.INVITER, inviter)
                .set(GAME.INVITEE, invitee)
                .set(GAME.INVITER_COLOR, inviterColor)
                .set(GAME.IS_RATED, true)
                .set(GAME.CURRENT_FEN, "test-fen")
                .set(GAME.CURRENT_HALF_MOVE_INDEX, 42)
                .set(GAME.INVITER_RATING_FROM, 1000)
                .set(GAME.INVITEE_RATING_FROM, 1000)
                .set(GAME.OUTCOME, outcome)
                .set(GAME.CREATED, now)
                .set(GAME.LAST_UPDATED, now)
                .set(GAME.GAME_STATUS, gameStatus)
                .set(GAME.TIME_CONTROL_CATEGORY, TimeControlCategory.BLITZ)
                .set(GAME.TIME_CONTROL_MODE, TimeControlMode.GAME_TIME)
                .set(GAME.TIME_CONTROL_BASE, 300)
                .set(GAME.TIME_CONTROL_INCREMENT, 3)
                .awaitExecute()
        }

        insertGame(
            id = "g1$userId".take(12),
            inviter = userId,
            invitee = opponentId,
            inviterColor = Color.RED,
            outcome = Outcome.RED_WINS
        )
        insertGame(
            id = "g2$userId".take(12),
            inviter = userId,
            invitee = opponentId,
            inviterColor = Color.BLACK,
            outcome = Outcome.RED_WINS
        )
        insertGame(
            id = "g3$userId".take(12),
            inviter = opponentId,
            invitee = userId,
            inviterColor = Color.RED,
            outcome = Outcome.DRAW
        )
        insertGame(
            id = "g4$userId".take(12),
            inviter = otherUserId,
            invitee = opponentId,
            inviterColor = Color.RED,
            outcome = Outcome.RED_WINS
        )

        val stats = userDaoService.fetchPlayerVsPlayerOutcomeStats(userId)
        assertEquals(1, stats.wins)
        assertEquals(1, stats.losses)
        assertEquals(1, stats.draws)
    }

    private suspend fun assertUnsubscribedToAll(email: String) {
        val user = dslContext.selectFrom(USER)
            .where(USER.EMAIL.eq(email))
            .awaitSingleMappedRecord<User>()!!

        assertFalse(user.emailNotificationEnabledNewsletter)
        assertFalse(user.emailNotificationEnabledUserJoinedGame)
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
        assertTrue(user.emailNotificationEnabledOpponentPlayedMove)
        assertTrue(user.emailNotificationEnabledOpponentResigned)
        assertTrue(user.emailNotificationEnabledOpponentProposedDraw)
        assertTrue(user.emailNotificationEnabledOpponentAcceptedDraw)
        assertTrue(user.emailNotificationEnabledOpponentDeclinedDraw)
    }

}
