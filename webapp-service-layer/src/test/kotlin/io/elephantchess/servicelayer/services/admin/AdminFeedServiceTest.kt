package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_MOVE
import io.elephantchess.db.dao.codegen.Tables.GAME_STATUS_EVENT
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.ServiceTest
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminFeedServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val adminFeedService by inject<AdminFeedService>()

    @AfterTest
    fun afterTest() = runTest {
        listOf(GAME_MOVE, GAME_STATUS_EVENT, GAME, USER)
            .forEach { table ->
                dslContext
                    .deleteFrom(table)
                    .awaitExecute()
            }
    }

    @Test
    fun `listLastManchuGames should only return manchu games`() = runTest {
        val inviter = UserId(AUTHENTICATED, signUpTestUser().second)
        val invitee = UserId(AUTHENTICATED, signUpTestUser().second)

        val manchuGameId = createAndJoinGame(inviter, invitee, variant = Variant.MANCHU)
        val xiangqiGameId = createAndJoinGame(inviter, invitee, variant = Variant.XIANGQI)

        val response = adminFeedService.listLastManchuGames()
        val gameIds = response.entries.map { it.gameId }

        assertTrue(gameIds.contains(manchuGameId))
        assertFalse(gameIds.contains(xiangqiGameId))
        assertTrue(response.entries.all { it.variant == Variant.MANCHU })
    }
}
