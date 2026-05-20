package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.model.GameEventType.CHECKMATED
import io.elephantchess.model.GameEventType.DRAW_ACCEPTED
import io.elephantchess.model.Outcome
import io.elephantchess.model.TimeControlCategory
import io.elephantchess.model.TimeControlMode
import io.elephantchess.servicelayer.services.ServiceTest
import io.elephantchess.xiangqi.Color
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import org.koin.core.component.inject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock

class PlayerVsPlayerGameDaoServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val pvpGameDaoService by inject<PlayerVsPlayerGameDaoService>()

    @Test
    fun `fetchPlayerVsPlayerOutcomeStatsPerCategory should count outcomes for each category`() = runTest {
        val userId = signUpTestUser().second
        val opponentId = signUpTestUser().second
        val otherUserId = signUpTestUser().second
        val now = Clock.System.now()

        suspend fun insertGame(
            inviter: String,
            invitee: String,
            inviterColor: Color,
            outcome: Outcome,
            category: TimeControlCategory,
        ) {
            val gameStatus = if (outcome == Outcome.DRAW) DRAW_ACCEPTED else CHECKMATED
            val id = randomAlphanumeric(12)
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
                .set(GAME.TIME_CONTROL_CATEGORY, category)
                .set(GAME.TIME_CONTROL_MODE, TimeControlMode.GAME_TIME)
                .set(GAME.TIME_CONTROL_BASE, 300)
                .set(GAME.TIME_CONTROL_INCREMENT, 3)
                .awaitExecute()
        }

        insertGame(
            inviter = userId,
            invitee = opponentId,
            inviterColor = Color.RED,
            outcome = Outcome.RED_WINS,
            category = TimeControlCategory.BLITZ
        )
        insertGame(
            inviter = userId,
            invitee = opponentId,
            inviterColor = Color.BLACK,
            outcome = Outcome.RED_WINS,
            category = TimeControlCategory.BLITZ
        )
        insertGame(
            inviter = opponentId,
            invitee = userId,
            inviterColor = Color.RED,
            outcome = Outcome.DRAW,
            category = TimeControlCategory.BULLET
        )
        insertGame(
            inviter = otherUserId,
            invitee = opponentId,
            inviterColor = Color.RED,
            outcome = Outcome.RED_WINS,
            category = TimeControlCategory.RAPID
        )

        val statsByCategory = pvpGameDaoService.fetchPlayerVsPlayerOutcomeStatsPerCategory(userId)
            .associateBy { it.category }

        val blitzStats = assertNotNull(statsByCategory[TimeControlCategory.BLITZ])
        assertEquals(1, blitzStats.wins)
        assertEquals(1, blitzStats.losses)
        assertEquals(0, blitzStats.draws)

        val bulletStats = assertNotNull(statsByCategory[TimeControlCategory.BULLET])
        assertEquals(0, bulletStats.wins)
        assertEquals(0, bulletStats.losses)
        assertEquals(1, bulletStats.draws)
    }

}
