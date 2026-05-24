package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_CHAT_MESSAGE
import io.elephantchess.db.dao.codegen.Tables.GAME_CHAT_TYPING_STATUS
import io.elephantchess.db.dao.codegen.Tables.GAME_MOVE
import io.elephantchess.db.dao.codegen.Tables.GAME_STATUS_EVENT
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.dto.game.JoinGameRequest
import io.elephantchess.servicelayer.dto.game.PlayMoveRequest
import io.elephantchess.servicelayer.dto.ws.PlayerVsPlayerInput
import io.elephantchess.servicelayer.dto.ws.PlayerVsPlayerUpdate
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.testutils.GameMovesDto
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PlayerVsPlayerGameServiceRefreshIntegrationTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()

    private lateinit var userId1: UserId
    private lateinit var userId2: UserId
    private lateinit var userId3: UserId
    private lateinit var userId4: UserId

    @BeforeTest
    fun before() = runBlocking {
        userId1 = UserId(AUTHENTICATED, signUpTestUser(11).second)
        userId2 = UserId(AUTHENTICATED, signUpTestUser(12).second)
        userId3 = UserId(AUTHENTICATED, signUpTestUser(13).second)
        userId4 = UserId(AUTHENTICATED, signUpTestUser(14).second)

        userService.refreshIsOnlineCache()
    }

    @AfterTest
    fun afterTest() = runBlocking {
        listOf(GAME_CHAT_TYPING_STATUS, GAME_CHAT_MESSAGE, GAME_MOVE, GAME_STATUS_EVENT, GAME, USER)
            .forEach { table ->
                dslContext
                    .deleteFrom(table)
                    .awaitExecute()
            }
    }

    @Test
    fun `multiple simultaneous games refresh their websocket sessions independently`() = runBlocking {
        val gameA = createAndJoinGame(userId1, userId2)
        val gameB = createAndJoinGame(userId3, userId4)

        val movesA = gameMovesCache.findByGameId("iaxeugSr")
        val movesB = gameMovesCache.findByGameId("NxJUgj53")

        val sessionA1 = startSession(gameA, userId1)
        val sessionA2 = startSession(gameA, userId2)
        val sessionB1 = startSession(gameB, userId3)
        val sessionB2 = startSession(gameB, userId4)

        try {
            waitForRefreshTick()
            listOf(sessionA1, sessionA2, sessionB1, sessionB2).forEach { it.channel.drain() }

            // First synchronized refresh: both games receive their first moves.
            playMove(gameA, userId1, movesA.uciMoves[0])
            playMove(gameB, userId3, movesB.uciMoves[0])

            val updateA1 = sessionA1.channel.awaitUpdate(movesA.uciMoves[0], 1)
            val updateA2 = sessionA2.channel.awaitUpdate(movesA.uciMoves[0], 1)
            val updateB1 = sessionB1.channel.awaitUpdate(movesB.uciMoves[0], 1)
            val updateB2 = sessionB2.channel.awaitUpdate(movesB.uciMoves[0], 1)

            assertMoveUpdate(updateA1, movesA, 1)
            assertMoveUpdate(updateA2, movesA, 1)
            assertMoveUpdate(updateB1, movesB, 1)
            assertMoveUpdate(updateB2, movesB, 1)

            assertEquals(1, pvpGameService.fetchGame(gameA).moveIndex)
            assertEquals(1, pvpGameService.fetchGame(gameB).moveIndex)

            // Second synchronized refresh: both games advance again.
            waitForRefreshTick()
            listOf(sessionA1, sessionA2, sessionB1, sessionB2).forEach { it.channel.drain() }

            playMove(gameA, userId2, movesA.uciMoves[1])
            playMove(gameB, userId4, movesB.uciMoves[1])

            val updateA1Second = sessionA1.channel.awaitUpdate(movesA.uciMoves[1], 2)
            val updateA2Second = sessionA2.channel.awaitUpdate(movesA.uciMoves[1], 2)
            val updateB1Second = sessionB1.channel.awaitUpdate(movesB.uciMoves[1], 2)
            val updateB2Second = sessionB2.channel.awaitUpdate(movesB.uciMoves[1], 2)

            assertMoveUpdate(updateA1Second, movesA, 2)
            assertMoveUpdate(updateA2Second, movesA, 2)
            assertMoveUpdate(updateB1Second, movesB, 2)
            assertMoveUpdate(updateB2Second, movesB, 2)

            assertEquals(2, pvpGameService.fetchGame(gameA).moveIndex)
            assertEquals(2, pvpGameService.fetchGame(gameB).moveIndex)

            // Third refresh: only game A advances; game B stays untouched.
            waitForRefreshTick()
            listOf(sessionA1, sessionA2, sessionB1, sessionB2).forEach { it.channel.drain() }

            playMove(gameA, userId1, movesA.uciMoves[2])

            val updateA1Third = sessionA1.channel.awaitUpdate(movesA.uciMoves[2], 3)
            val updateA2Third = sessionA2.channel.awaitUpdate(movesA.uciMoves[2], 3)

            assertMoveUpdate(updateA1Third, movesA, 3)
            assertMoveUpdate(updateA2Third, movesA, 3)
            assertTrue(sessionB1.channel.tryReceive().isFailure, "game B session should not receive a game A update")
            assertTrue(sessionB2.channel.tryReceive().isFailure, "game B session should not receive a game A update")

            assertEquals(3, pvpGameService.fetchGame(gameA).moveIndex)
            assertEquals(2, pvpGameService.fetchGame(gameB).moveIndex)
        } finally {
            listOf(sessionA1, sessionA2, sessionB1, sessionB2).forEach { pvpGameService.closePlayerVsPlayerSession(it.sessionId) }
        }
    }

    @Test
    fun `chat and typing refresh to websocket sessions`() {
        runBlocking {
            val gameId = createAndJoinGame(userId1, userId2)
            val sessionViewer = startSession(gameId, userId1)
            val sessionActor = startSession(gameId, userId2)

            try {
                waitForRefreshTick()
                sessionViewer.channel.drain()
                sessionActor.channel.drain()

                pvpGameService.handlePlayerVsPlayerInput(
                    userId = userId2,
                    gameId = gameId,
                    input = PlayerVsPlayerInput(isTyping = true)
                )
                pvpGameService.handlePlayerVsPlayerInput(
                    userId = userId2,
                    gameId = gameId,
                    input = PlayerVsPlayerInput(message = "hello from user 2")
                )

                pvpGameService.refreshPlayerVsPlayerSessionsForTest()

                val viewerUpdate = sessionViewer.channel.awaitNextUpdate()
                val actorUpdate = sessionActor.channel.awaitNextUpdate()

                assertEquals(1, viewerUpdate.chatMessages.size)
                assertEquals("hello from user 2", viewerUpdate.chatMessages.single().content)
                assertEquals(1, viewerUpdate.typingUsers.size)
                assertEquals(userId2.id, viewerUpdate.typingUsers.single().userId)

                assertEquals(1, actorUpdate.chatMessages.size)
                assertEquals("hello from user 2", actorUpdate.chatMessages.single().content)
                assertTrue(actorUpdate.typingUsers.isEmpty())

                val chatHistory = pvpGameService.fetchChatHistory(gameId)
                assertEquals(1, chatHistory.messages.size)
                assertEquals("hello from user 2", chatHistory.messages.single().content)

                assertNotNull(pvpGameService.fetchGame(gameId))
            } finally {
                listOf(sessionViewer, sessionActor).forEach { pvpGameService.closePlayerVsPlayerSession(it.sessionId) }
            }
        }
    }


    private suspend fun playMove(gameId: String, userId: UserId, move: String) {
        pvpGameService.playMove(userId.id, PlayMoveRequest(gameId, move))
    }

    private suspend fun createAndJoinGame(inviter: UserId, invitee: UserId): String {
        val response = pvpGameService.createGame(
            inviter,
            CreateGameRequest(
                inviterColor = RED,
                isRated = true,
                timeControlBase = 30.minutes.inWholeSeconds.toInt(),
                timeControlIncrement = null,
                timeControlMode = io.elephantchess.model.TimeControlMode.GAME_TIME,
                allowGuests = true,
                alwaysVisibleInLobby = false,
                privateInvite = true,
            )
        )

        pvpGameService.joinGame(invitee, JoinGameRequest(response.gameId))
        return response.gameId
    }

    private suspend fun startSession(gameId: String, userId: UserId): SessionHandle {
        val channel = Channel<PlayerVsPlayerUpdate>(Channel.UNLIMITED)
        val sessionId = pvpGameService.startPlayerVsPlayerSession(userId, gameId) { update ->
            channel.trySend(update)
        }

        return SessionHandle(sessionId = sessionId, channel = channel)
    }

    private fun waitForRefreshTick() {
        runBlocking {
            pvpGameService.refreshPlayerVsPlayerSessionsForTest()
        }
    }

    private fun Channel<PlayerVsPlayerUpdate>.drain() {
        while (tryReceive().getOrNull() != null) {
            // drain all pending updates before the next phase
        }
    }

    private fun Channel<PlayerVsPlayerUpdate>.awaitUpdate(expectedMove: String, expectedIndex: Int): PlayerVsPlayerUpdate {
        val deadline = System.currentTimeMillis() + 4_000

        while (System.currentTimeMillis() < deadline) {
            var update = tryReceive().getOrNull()
            while (update != null) {
                val move = update.newMove
                if (move != null && move.move == expectedMove && move.updatedIndex == expectedIndex) {
                    return update
                }
                update = tryReceive().getOrNull()
            }

            Thread.sleep(25)
        }

        error("Timed out waiting for update move=$expectedMove index=$expectedIndex")
    }

    private fun Channel<PlayerVsPlayerUpdate>.awaitNextUpdate(): PlayerVsPlayerUpdate {
        val deadline = System.currentTimeMillis() + 4_000

        while (System.currentTimeMillis() < deadline) {
            val update = tryReceive().getOrNull()
            if (update != null) {
                return update
            }

            Thread.sleep(25)
        }

        error("Timed out waiting for websocket update")
    }


    private fun assertMoveUpdate(update: PlayerVsPlayerUpdate, moves: GameMovesDto, expectedIndex: Int) {
        val newMove = requireNotNull(update.newMove)
        assertEquals(moves.uciMoves[expectedIndex - 1], newMove.move)
        assertEquals(expectedIndex, newMove.updatedIndex)
    }

    private data class SessionHandle(
        val sessionId: String,
        val channel: Channel<PlayerVsPlayerUpdate>,
    )

}
