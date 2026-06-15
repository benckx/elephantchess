package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.model.TimeControlCategory
import io.elephantchess.model.TimeControlCategory.BLITZ
import io.elephantchess.model.TimeControlCategory.BULLET
import io.elephantchess.model.TimeControlCategory.CLASSICAL
import io.elephantchess.servicelayer.services.ServiceTest
import io.elephantchess.xiangqi.Variant
import io.elephantchess.xiangqi.Variant.MANCHU
import io.elephantchess.xiangqi.Variant.XIANGQI
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminUserEloServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val adminUserEloService by inject<AdminUserEloService>()

    @Test
    fun `listUserEloStats returns min avg max users per variant and time control`() = runTest {
        val (_, user1) = signUpTestUser(1_001)
        val (_, user2) = signUpTestUser(1_002)
        val (_, user3) = signUpTestUser(1_003)

        dslContext
            .update(USER)
            .set(USER.GAME_RATING_BULLET, 900)
            .set(USER.GAME_RATING_MANCHU_BLITZ, 1_200)
            .where(USER.ID.eq(user1))
            .awaitExecute()

        dslContext
            .update(USER)
            .set(USER.GAME_RATING_BULLET, 1_100)
            .set(USER.GAME_RATING_MANCHU_BLITZ, 1_400)
            .where(USER.ID.eq(user2))
            .awaitExecute()

        dslContext
            .update(USER)
            .set(USER.GAME_RATING_BULLET, 1_300)
            .set(USER.GAME_RATING_MANCHU_BLITZ, 1_600)
            .where(USER.ID.eq(user3))
            .awaitExecute()

        val result = adminUserEloService.listUserEloStats()

        assertEquals(TimeControlCategory.entries.size * Variant.entries.size, result.entries.size)

        val xiangqiBullet = result.entries.single { it.variant == XIANGQI && it.timeControlCategory == BULLET }
        assertEquals(3, xiangqiBullet.userCount)
        assertEquals(1_100.0, xiangqiBullet.averageRating)
        assertEquals("test1001", xiangqiBullet.minUsername)
        assertEquals(900, xiangqiBullet.minRating)
        assertEquals("test1003", xiangqiBullet.maxUsername)
        assertEquals(1_300, xiangqiBullet.maxRating)

        val manchuBlitz = result.entries.single { it.variant == MANCHU && it.timeControlCategory == BLITZ }
        assertEquals(3, manchuBlitz.userCount)
        assertEquals(1_400.0, manchuBlitz.averageRating)
        assertEquals("test1001", manchuBlitz.minUsername)
        assertEquals(1_200, manchuBlitz.minRating)
        assertEquals("test1003", manchuBlitz.maxUsername)
        assertEquals(1_600, manchuBlitz.maxRating)

        val xiangqiClassical = result.entries.single { it.variant == XIANGQI && it.timeControlCategory == CLASSICAL }
        assertEquals(3, xiangqiClassical.userCount)
        assertEquals(1_000.0, xiangqiClassical.averageRating)
        assertEquals(1_000, xiangqiClassical.minRating)
        assertEquals(1_000, xiangqiClassical.maxRating)
    }

}
