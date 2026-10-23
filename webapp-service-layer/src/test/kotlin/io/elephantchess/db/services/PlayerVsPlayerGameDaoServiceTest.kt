package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_MOVE
import io.elephantchess.db.dao.codegen.Tables.GAME_STATUS_EVENT
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.instantOfUtc
import io.elephantchess.model.GameEventType.JOINED
import io.elephantchess.model.GameJoinSource
import io.elephantchess.model.GameJoinSource.DISCORD_NOTIFICATION
import io.elephantchess.model.GameJoinSource.DYNAMIC_MATCHED
import io.elephantchess.model.GameJoinSource.LINK
import io.elephantchess.model.GameJoinSource.LOBBY
import io.elephantchess.model.GameJoinSource.MATCHED
import io.elephantchess.model.TimeControlMode.GAME_TIME
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.dto.game.JoinGameRequest
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.ServiceTest
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.Variant.XIANGQI
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.time.YearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlayerVsPlayerGameDaoServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val pvpGameDaoService by inject<PlayerVsPlayerGameDaoService>()

    private val lobbyJoinSources = listOf(LOBBY, MATCHED, DYNAMIC_MATCHED)
    private val linkJoinSources = listOf(LINK)

    @AfterTest
    fun afterTest() =
        runTest {
            listOf(GAME_MOVE, GAME_STATUS_EVENT, GAME, USER)
                .forEach { table -> dslContext.deleteFrom(table).awaitExecute() }
        }

    @Test
    fun `computes average matching time per month and join source`() =
        runTest {
            val inviterId = UserId(AUTHENTICATED, signUpTestUser().second)
            val inviteeId = UserId(AUTHENTICATED, signUpTestUser().second)

            val created = instantOfUtc(2024, 3, 10, 12, 0, 0)

            // two lobby games with 60s and 120s matching time, both over 3 moves
            createJoinedGameWithMatchingTime(inviterId, inviteeId, LOBBY, created, matchingSeconds = 60, halfMoves = 10)
            createJoinedGameWithMatchingTime(inviterId, inviteeId, LOBBY, created, matchingSeconds = 120, halfMoves = 10)

            // one link game with 300s matching time, over 3 moves
            createJoinedGameWithMatchingTime(inviterId, inviteeId, LINK, created, matchingSeconds = 300, halfMoves = 10)

            // a lobby game under the move threshold: must be ignored
            createJoinedGameWithMatchingTime(inviterId, inviteeId, LOBBY, created, matchingSeconds = 9000, halfMoves = 2)

            val lobbyAvg = pvpGameDaoService.avgMatchingTimeSecondsByMonthForJoinSources(6, lobbyJoinSources)
            val linkAvg = pvpGameDaoService.avgMatchingTimeSecondsByMonthForJoinSources(6, linkJoinSources)

            val month = YearMonth.of(2024, 3)

            assertEquals(1, lobbyAvg.size)
            assertEquals(month, lobbyAvg.first().month)
            assertEquals(90.0, lobbyAvg.first().value.toDouble(), 0.001)

            assertEquals(1, linkAvg.size)
            assertEquals(month, linkAvg.first().month)
            assertEquals(300.0, linkAvg.first().value.toDouble(), 0.001)
        }

    @Test
    fun `ignores games whose join source is not requested`() =
        runTest {
            val inviterId = UserId(AUTHENTICATED, signUpTestUser().second)
            val inviteeId = UserId(AUTHENTICATED, signUpTestUser().second)

            val created = instantOfUtc(2024, 4, 10, 12, 0, 0)

            createJoinedGameWithMatchingTime(inviterId, inviteeId, DISCORD_NOTIFICATION, created, matchingSeconds = 42, halfMoves = 10)

            val lobbyAvg = pvpGameDaoService.avgMatchingTimeSecondsByMonthForJoinSources(6, lobbyJoinSources)
            val linkAvg = pvpGameDaoService.avgMatchingTimeSecondsByMonthForJoinSources(6, linkJoinSources)

            assertTrue(lobbyAvg.isEmpty())
            assertTrue(linkAvg.isEmpty())
        }

    private suspend fun createJoinedGameWithMatchingTime(
        inviter: UserId,
        invitee: UserId,
        source: GameJoinSource,
        created: Instant,
        matchingSeconds: Long,
        halfMoves: Int,
    ): String {
        val gameId = createAndJoinGame(inviter, invitee, source)

        dslContext
            .update(GAME)
            .set(GAME.CREATED, created)
            .set(GAME.CURRENT_HALF_MOVE_INDEX, halfMoves)
            .where(GAME.ID.eq(gameId))
            .awaitExecute()

        dslContext
            .update(GAME_STATUS_EVENT)
            .set(GAME_STATUS_EVENT.EVENT_TIME, created.plus(matchingSeconds.seconds))
            .where(GAME_STATUS_EVENT.GAME_ID.eq(gameId))
            .and(GAME_STATUS_EVENT.EVENT_TYPE.eq(JOINED))
            .awaitExecute()

        return gameId
    }

    private suspend fun createAndJoinGame(inviter: UserId, invitee: UserId, source: GameJoinSource): String {
        val response = pvpGameService.createGame(
            inviter,
            CreateGameRequest(
                inviterColor = RED,
                isRated = true,
                timeControlBase = 1800,
                timeControlIncrement = null,
                timeControlMode = GAME_TIME,
                allowGuests = true,
                alwaysVisibleInLobby = false,
                privateInvite = true,
                variant = XIANGQI,
            ),
        )
        pvpGameService.joinGame(invitee, JoinGameRequest(response.gameId, source))
        return response.gameId
    }
}
