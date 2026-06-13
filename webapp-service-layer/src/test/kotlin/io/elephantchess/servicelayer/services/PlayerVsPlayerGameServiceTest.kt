package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameEventType.*
import io.elephantchess.model.Outcome
import io.elephantchess.model.Outcome.BLACK_WINS
import io.elephantchess.model.Outcome.RED_WINS
import io.elephantchess.model.TimeControlMode
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.model.UserType.GUEST
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.dto.game.JoinGameRequest
import io.elephantchess.servicelayer.dto.game.PlayMoveRequest
import io.elephantchess.servicelayer.dto.user.NotificationsSettingsDto
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.Variant
import io.elephantchess.xiangqi.testutils.Ops.endsInCheckmate
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PlayerVsPlayerGameServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val userDaoService by inject<UserDaoService>()
    private val mailService by inject<MailService>()

    private lateinit var userId1: UserId
    private lateinit var userId2: UserId
    private lateinit var guestId1: UserId
    private lateinit var guestId2: UserId

    @BeforeTest
    fun before() = runTest {
        userId1 = UserId(AUTHENTICATED, signUpTestUser().second)
        userId2 = UserId(AUTHENTICATED, signUpTestUser().second)
        guestId1 = UserId(GUEST, userService.obtainGuestUserToken().id)
        guestId2 = UserId(GUEST, userService.obtainGuestUserToken().id)

        userService.refreshIsOnlineCache()
    }

    @AfterTest
    fun afterTest() = runTest {
        listOf(GAME_MOVE, GAME_STATUS_EVENT, GAME, USER)
            .forEach { table ->
                dslContext
                    .deleteFrom(table)
                    .awaitExecute()
            }
    }

    /**
     * If both players want the exact same game,
     * the second "create" request ends up joining the first game
     */
    @Test
    fun joinMatchingGameTest01() = runTest {
        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )

        val request2 = request1.copy(inviterColor = BLACK)

        val response1 = pvpGameService.createGame(userId1, request1)
        val response2 = pvpGameService.createGame(userId2, request2)

        assertEquals(CREATED, response1.eventType)
        assertEquals(RED, response1.color)

        assertEquals(JOINED, response2.eventType)
        assertEquals(BLACK, response2.color)

        assertEquals(1, countGameByStatus(JOINED))
        assertEquals(0, countGameByStatus(CREATED))
    }

    @Test
    fun `'always visible in lobby' accepted if all conditions are met`() = runTest {
        assertFalse(mailService.isEmailAddressValid(fetchEmailOf(userId1.id)))
        assertFalse(isOptionAlwaysVisibleInLobbyAllowed(userId1.id))

        makeAlwaysVisibleInLobbyAllowed(userId1.id)
        assertTrue(mailService.isEmailAddressValid(fetchEmailOf(userId1.id)))
        assertTrue(isOptionAlwaysVisibleInLobbyAllowed(userId1.id))

        val response = pvpGameService.createGame(userId1, alwaysVisibleInLobbyRapidPublicGameRequest())

        assertEquals(CREATED, response.eventType)
        assertTrue(fetchAlwaysVisibleInLobby(response.gameId))
    }

    @Test
    fun `'always visible in lobby' rejected if email is not valid`() = runTest {
        assertFalse(mailService.isEmailAddressValid(fetchEmailOf(userId1.id)))
        assertFalse(isOptionAlwaysVisibleInLobbyAllowed(userId1.id))

        val e = assertFailsWith<BadRequestException> {
            pvpGameService.createGame(userId1, alwaysVisibleInLobbyRapidPublicGameRequest())
        }

        logger.info { "expected exception $e" }
        assertTrue(e.message!!.startsWith("The 'always show in lobby' option is not allowed for this game"))
    }

    @Test
    fun `'always visible in lobby' rejected if email is not valid, even if notification is true`() = runTest {
        setOpponentJoinedGameNotification(userId1.id, enabled = true)
        assertFalse(isOptionAlwaysVisibleInLobbyAllowed(userId1.id))

        val e = assertFailsWith<BadRequestException> {
            pvpGameService.createGame(userId1, alwaysVisibleInLobbyRapidPublicGameRequest())
        }

        logger.info { "expected exception $e" }
        assertTrue(e.message!!.startsWith("The 'always show in lobby' option is not allowed for this game"))
    }

    @Test
    fun `'always visible in lobby' rejected if notification setting is false`() = runTest {
        confirmEmail(userId1.id)
        setOpponentJoinedGameNotification(userId1.id, enabled = false)
        assertFalse(isOptionAlwaysVisibleInLobbyAllowed(userId1.id))

        val e = assertFailsWith<BadRequestException> {
            pvpGameService.createGame(userId1, alwaysVisibleInLobbyRapidPublicGameRequest())
        }

        logger.info { "expected exception $e" }
        assertTrue(e.message!!.startsWith("The 'always show in lobby' option is not allowed for this game"))
    }

    @Test
    fun `'always visible in lobby' always rejected for guest`() = runTest {
        val e = assertFailsWith<BadRequestException> {
            pvpGameService.createGame(guestId1, alwaysVisibleInLobbyRapidPublicGameRequest())
        }

        logger.info { "expected exception $e" }
        assertEquals("Guest users are not allowed to use the 'always show in lobby' option", e.message)
    }

    @Test
    fun `'always visible in lobby' rejected if conditions are not met, even for correspondence games`() = runTest {
        val e = assertFailsWith<BadRequestException> {
            pvpGameService.createGame(
                userId1,
                alwaysVisibleInLobbyRapidPublicGameRequest().copy(
                    timeControlMode = TimeControlMode.MOVE_TIME,
                    timeControlBase = 1.days.inWholeSeconds.toInt(),
                )
            )
        }

        logger.info { "expected exception $e" }
        assertTrue(e.message!!.startsWith("The 'always show in lobby' option is not allowed for this game"))
    }

    /**
     * If both players want to play with the same color,
     * the games are incompatible
     */
    @Test
    fun joinMatchingGameIncompatibleTest01() = runTest {
        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )

        val request2 = request1.copy()

        val response1 = pvpGameService.createGame(userId1, request1)
        val response2 = pvpGameService.createGame(userId2, request2)

        assertEquals(CREATED, response1.eventType)
        assertEquals(RED, response1.color)

        assertEquals(CREATED, response2.eventType)
        assertEquals(RED, response2.color)

        assertEquals(0, countGameByStatus(JOINED))
        assertEquals(2, countGameByStatus(CREATED))
    }

    /**
     * If both players want different rating modes,
     * the games are incompatible
     */
    @Test
    fun joinMatchingGameIncompatibleTest02() = runTest {
        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )

        val request2 = request1.copy(isRated = false)

        val response1 = pvpGameService.createGame(userId1, request1)
        val response2 = pvpGameService.createGame(userId2, request2)

        assertEquals(CREATED, response1.eventType)
        assertEquals(RED, response1.color)

        assertEquals(CREATED, response2.eventType)
        assertEquals(RED, response2.color)

        assertEquals(0, countGameByStatus(JOINED))
        assertEquals(2, countGameByStatus(CREATED))
    }

    /**
     * If both players want different time controls,
     * the games are incompatible
     */
    @Test
    fun joinMatchingGameIncompatibleTest03() = runTest {
        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )

        val request2 = request1.copy(timeControlBase = 10.minutes.inWholeSeconds.toInt())

        val response1 = pvpGameService.createGame(userId1, request1)
        val response2 = pvpGameService.createGame(userId2, request2)

        assertEquals(CREATED, response1.eventType)
        assertEquals(RED, response1.color)

        assertEquals(CREATED, response2.eventType)
        assertEquals(RED, response2.color)

        assertEquals(0, countGameByStatus(JOINED))
        assertEquals(2, countGameByStatus(CREATED))
    }

    /**
     * If first game is not allowed to guests,
     * the game created by a guest won't match
     */
    @Test
    fun joinMatchingGameIncompatibleTest04() = runTest {
        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = false,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )

        val request2 = request1.copy(inviterColor = null, allowGuests = true)
        val request3 = request2.copy()

        val response1 = pvpGameService.createGame(userId1, request1)
        val response2 = pvpGameService.createGame(guestId1, request2)
        val response3 = pvpGameService.createGame(userId2, request3)

        assertEquals(CREATED, response1.eventType)
        assertEquals(RED, response1.color)

        assertEquals(CREATED, response2.eventType)
        assertEquals(null, response2.color)

        assertEquals(JOINED, response3.eventType)
        assertEquals(BLACK, response3.color)

        assertEquals(1, countGameByStatus(JOINED))
        assertEquals(1, countGameByStatus(CREATED))
    }

    /**
     * User should not be match with another game they created
     */
    @Test
    fun joinMatchingGameIncompatibleTest05() = runTest {
        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = false,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )

        val request2 = request1.copy(inviterColor = null)

        val response1 = pvpGameService.createGame(userId1, request1)
        val response2 = pvpGameService.createGame(userId1, request2)

        assertEquals(CREATED, response1.eventType)
        assertEquals(RED, response1.color)

        assertEquals(CREATED, response2.eventType)
        assertEquals(null, response2.color)

        assertEquals(0, countGameByStatus(JOINED))
        assertEquals(2, countGameByStatus(CREATED))
    }

    /**
     * If a game is marked as private,
     * it should not be matched automatically
     */
    @Test
    fun joinMatchingGameIncompatibleTest06() = runTest {
        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = true
        )

        val request2 = request1.copy(inviterColor = BLACK, privateInvite = false)

        val response1 = pvpGameService.createGame(userId1, request1)
        val response2 = pvpGameService.createGame(userId2, request2)

        assertEquals(CREATED, response1.eventType)
        assertEquals(RED, response1.color)

        assertEquals(CREATED, response2.eventType)
        assertEquals(BLACK, response2.color)

        assertEquals(0, countGameByStatus(JOINED))
        assertEquals(2, countGameByStatus(CREATED))
    }

    /**
     * Two users created compatible games while not being online at the same
     * time (so the regular matching at creation time did not pair them).
     * When both users come back online, the dynamic matching routine should
     * pair the two games together.
     */
    @Test
    fun dynamicMatchingTest01() = runTest {
        // user2 is offline when user1 creates their game
        setUserOffline(userId2.id)
        userService.refreshIsOnlineCache()

        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )

        val response1 = pvpGameService.createGame(userId1, request1)
        assertEquals(CREATED, response1.eventType)

        // user1 is offline when user2 creates their compatible game
        setUserOffline(userId1.id)
        setUserOnline(userId2.id)
        userService.refreshIsOnlineCache()

        val response2 = pvpGameService.createGame(userId2, request1.copy(inviterColor = BLACK))
        assertEquals(CREATED, response2.eventType)

        // both games remain pending because the other inviter was offline
        assertEquals(0, countGameByStatus(JOINED))
        assertEquals(2, countGameByStatus(CREATED))

        // both users come back online → dynamic matching should pair them
        setUserOnline(userId1.id)
        setUserOnline(userId2.id)
        userService.refreshIsOnlineCache()

        pvpGameService.findDynamicMatches(setOf(userId1.id, userId2.id))

        assertEquals(1, countGameByStatus(JOINED))
        assertEquals(0, countGameByStatus(CREATED))
        assertEquals(1, countGameByStatus(AUTO_CANCELED))
    }

    /**
     * If two pending games are incompatible (e.g. both inviters want the same
     * color) dynamic matching should not pair them, even when both inviters
     * are online.
     */
    @Test
    fun dynamicMatchingIncompatibleTest01() = runTest {
        // user2 is offline when user1 creates their game
        setUserOffline(userId2.id)
        userService.refreshIsOnlineCache()

        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )

        val response1 = pvpGameService.createGame(userId1, request1)
        assertEquals(CREATED, response1.eventType)

        // user1 is offline when user2 creates a game with the same color
        setUserOffline(userId1.id)
        setUserOnline(userId2.id)
        userService.refreshIsOnlineCache()

        val response2 = pvpGameService.createGame(userId2, request1.copy())
        assertEquals(CREATED, response2.eventType)

        // both back online; games are incompatible (both RED) → no match
        setUserOnline(userId1.id)
        setUserOnline(userId2.id)
        userService.refreshIsOnlineCache()

        pvpGameService.findDynamicMatches(setOf(userId1.id, userId2.id))

        assertEquals(0, countGameByStatus(JOINED))
        assertEquals(2, countGameByStatus(CREATED))
        assertEquals(0, countGameByStatus(AUTO_CANCELED))
    }

    /**
     * A Manchu game and a Xiangqi game with compatible colors should NOT be matched together —
     * variant must be part of the matching criteria.
     */
    @Test
    fun joinMatchingGameIncompatibleTest07() = runTest {
        val manchuRequest = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false,
            variant = Variant.MANCHU
        )

        val xiangqiRequest = manchuRequest.copy(inviterColor = BLACK, variant = Variant.XIANGQI)

        val response1 = pvpGameService.createGame(userId1, manchuRequest)
        val response2 = pvpGameService.createGame(userId2, xiangqiRequest)

        assertEquals(CREATED, response1.eventType)
        assertEquals(RED, response1.color)

        assertEquals(CREATED, response2.eventType)
        assertEquals(BLACK, response2.color)

        assertEquals(0, countGameByStatus(JOINED))
        assertEquals(2, countGameByStatus(CREATED))
    }


    @Test
    fun happyPathTest01() = runTest {
        val gameId = createAndJoinGame(userId1, userId2, inviterColor = RED)
        val gameMoves = gameMovesCache.findByGameId("4Q815fbI")
        assertTrue { gameMoves.endsInCheckmate() }

        // all moves but the last one
        gameMoves.uciMoves
            .dropLast(1)
            .forEachIndexed { i, move ->
                val playMoveResult = pvpGameService.playMove(
                    userId = userIdToPlay(gameId),
                    request = PlayMoveRequest(gameId, move)
                )

                assertEquals(playMoveResult.updatedIndex, i + 1)
                assertEquals(move, playMoveResult.move)
                assertNull(playMoveResult.gameEventType)
                assertNull(playMoveResult.ratingUpdate)
            }

        pvpGameService.fetchGame(gameId).let { gameData ->
            assertEquals(JOINED, gameData.gameEventType)
            assertNull(gameData.outcome)
        }

        // last move
        val lastMoveResult = pvpGameService.playMove(
            userId = userIdToPlay(gameId),
            request = PlayMoveRequest(gameId, gameMoves.uciMoves.last())
        )

        assertEquals(lastMoveResult.gameEventType, CHECKMATED)

        // check rating update
        val ratingUpdate = lastMoveResult.ratingUpdate!!
        assertEquals(true, ratingUpdate.isRated)
        assertEquals(1_000, ratingUpdate.inviterRatingFrom)
        assertEquals(1_000, ratingUpdate.inviteeRatingFrom)

        val gameData = pvpGameService.fetchGame(gameId)
        assertEquals(gameData.gameEventType, CHECKMATED)
        assertNotNull(gameData.outcome)
        assertNotEquals(Outcome.DRAW, gameData.outcome)

        val inviterColor = gameData.inviterColor!!
        val expectedWinnerRating = 1_000 + 8
        val expectedLoserRating = 1_000 - 8

        val inviterWins =
            (gameData.outcome == RED_WINS && inviterColor == RED) ||
                    (gameData.outcome == BLACK_WINS && inviterColor == BLACK)

        val inviteeWins =
            (gameData.outcome == RED_WINS && inviterColor == BLACK) ||
                    (gameData.outcome == BLACK_WINS && inviterColor == RED)

        when {
            inviterWins -> {
                assertEquals(expectedWinnerRating, ratingUpdate.inviterRatingTo)
                assertEquals(expectedLoserRating, ratingUpdate.inviteeRatingTo)
            }

            inviteeWins -> {
                assertEquals(expectedLoserRating, ratingUpdate.inviterRatingTo)
                assertEquals(expectedWinnerRating, ratingUpdate.inviteeRatingTo)
            }
        }

        // Xiangqi RAPID DB ratings must reflect the exact +8 / -8 change
        val xiangqiRating1 = fetchXiangqiRapidRating(userId1.id)
        val xiangqiRating2 = fetchXiangqiRapidRating(userId2.id)

        val userId1IsInviter = gameData.inviterId == userId1.id
        when {
            inviterWins -> {
                if (userId1IsInviter) {
                    assertEquals(expectedWinnerRating, xiangqiRating1)
                    assertEquals(expectedLoserRating, xiangqiRating2)
                } else {
                    assertEquals(expectedLoserRating, xiangqiRating1)
                    assertEquals(expectedWinnerRating, xiangqiRating2)
                }
            }

            inviteeWins -> {
                if (userId1IsInviter) {
                    assertEquals(expectedLoserRating, xiangqiRating1)
                    assertEquals(expectedWinnerRating, xiangqiRating2)
                } else {
                    assertEquals(expectedWinnerRating, xiangqiRating1)
                    assertEquals(expectedLoserRating, xiangqiRating2)
                }
            }
        }

        // Manchu RAPID rating must remain unchanged at 1000
        assertEquals(1_000, fetchManchuRapidRating(userId1.id))
        assertEquals(1_000, fetchManchuRapidRating(userId2.id))
    }

    @Test
    fun `guests users not allowed to join games with option allowGuests == false`() = runTest {
        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = false,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )

        val response1 = pvpGameService.createGame(userId1, request1)
        assertEquals(CREATED, response1.eventType)

        val e = assertFailsWith<BadRequestException> {
            pvpGameService.joinGame(guestId1, JoinGameRequest(response1.gameId))
        }

        logger.info { "expected exception $e" }
        assertEquals("Guest users are not allowed to join this game", e.message)
    }

    @Test
    fun playMoveErrorHandlingTest01() = runTest {
        val gameId = createAndJoinGame(userId1, userId2, inviterColor = RED)
        val gameMoves = gameMovesCache.findByGameId("iaxeugSr")

        gameMoves.uciMoves.dropLast(10).forEach { move ->
            pvpGameService.playMove(
                userId = userIdToPlay(gameId),
                request = PlayMoveRequest(gameId, move)
            )
        }

        // user attempts to play the opponent color
        val e1 = assertFailsWith<BadRequestException> {
            val move = gameMoves.uciMoves[gameMoves.uciMoves.size - 10]
            val wrongUserId = if (userIdToPlay(gameId) == userId1.id) userId2.id else userId1.id

            pvpGameService.playMove(
                userId = wrongUserId,
                request = PlayMoveRequest(gameId, move)
            )
        }

        logger.info { "expected exception $e1" }
        assertEquals("It is RED turn to play and user ${userId2.id} plays BLACK", e1.message)

        // user attempts to play non-parsable move
        val e2 = assertFailsWith<BadRequestException> {
            pvpGameService.playMove(
                userId = userIdToPlay(gameId),
                request = PlayMoveRequest(
                    gameId = gameId,
                    move = "not a valid move"
                )
            )
        }

        logger.info { "expected exception $e2" }
        assertEquals("Move 'not a valid move' can not be parsed", e2.message)

        // user attempts to play illegal move
        val e3 = assertFailsWith<BadRequestException> {
            pvpGameService.playMove(
                userId = userIdToPlay(gameId),
                request = PlayMoveRequest(
                    gameId = gameId,
                    move = "a1b3"
                )
            )
        }

        logger.info { "expected exception $e3" }
        assertEquals("Move 'a1b3' is illegal", e3.message)
    }

    /**
     * In this game https://elephantchess.io/game?id=EYI11Ad81jKv
     * it looks some illegal moves were introduced in the history for some reason
     *
     * https://github.com/benckx/elephantchess.io/issues/252
     */
    @Test
    fun playMoveErrorHandlingTest02() = runTest {
        val moves = listOf("c3c4", "b9c7", "g3g4", "h7g7", "h0g2", "g7g4", "g2f4", "g6g5", "b2e2")
        val gameId = createAndJoinGame(userId1, userId2, inviterColor = RED)

        moves.forEach { move ->
            pvpGameService.playMove(
                userId = userIdToPlay(gameId),
                request = PlayMoveRequest(gameId, move)
            )
        }

        val e = assertFailsWith<BadRequestException> {
            pvpGameService.playMove(
                userId = userIdToPlay(gameId),
                request = PlayMoveRequest(gameId, "g4e4")
            )
        }

        logger.info { "expected exception $e" }
        assertEquals("Move 'g4e4' is illegal", e.message)
    }

    /**
     * Bugfix for perpetual check detection.
     *
     * https://github.com/benckx/elephantchess/issues/379
     * https://elephantchess.io/game?id=ctE1ItdFpFPE
     */
    @Test
    fun perpetualCheckRuleTest01() = runTest {
        val moves = listOf(
            "b0c2", "b9c7", "c3c4", "h9i7", "g3g4", "a9a8", "h0g2", "a8d8", "g2f4", "h7h4", "i3i4", "h4f4", "h2e2",
            "f4f2", "i0h0", "f2c2", "c4c5", "c6c5", "b2b4", "d8d3", "b4e4", "d9e8", "a0b0", "d3e3", "e4c4", "b7a7",
            "c4c7", "c2c7", "b0b9", "c7c0", "e0e1", "g9e7", "g4g5", "i9h9", "h0h9", "i7h9", "g5g6", "h9i7", "g6g7",
            "i7g6", "g7g8", "g6f4", "g8g9", "e7g9", "b9c9", "e8d9", "e1d1", "f9e8", "c9c5", "c0f0", "c5c6", "e3d3",
            "d1e1", "f4g2", "e1e0", "a7a3", "e2e8", "d9e8", "c6e6", "a3a0", "d0e1", "f0f4", "e6a6", "f4e4", "e1f0",
            "d3e3", "e0d0", "e3d3", "d0e0", "a0f0", "a6a9", "e8d9", "a9a4", "d3e3", "e0d0", "f0f4", "a4a2", "e3d3",
            "d0e0", "d3e3", "e0d0", "g2e1", "d0d1", "e1g0", "a2g2", "e3e1", "d1d2", "g0i1", "g2g9", "e9e8", "g9d9",
            "e4i4", "d9d8", "e8e7", "d8d7", "e7e8", "d7d8", "e8e7", "d8d7", "e7e8", "d7d8", "e8e7", "d8d7", "e7e8",
            "d7d8", "e8e7", "d8d7", "e7e8", "d7d8", "e8e7", "d8d7", "e7e8"
        )

        val gameId = createAndJoinGame(userId1, userId2, inviterColor = RED)
        moves.take(102).forEachIndexed { _, move ->
            pvpGameService.playMove(
                userId = userIdToPlay(gameId),
                request = PlayMoveRequest(gameId, move)
            )
        }

        val response = pvpGameService.playMove(
            userId = userIdToPlay(gameId),
            request = PlayMoveRequest(gameId, moves[102])
        )

        assertEquals(PERPETUAL_CHECKING, response.gameEventType)
    }

    /**
     * User should not be able to create more than 3 CREATED PvP games with the same settings.
     */
    @Test
    fun createGameLimitTest01() = runTest {
        val request = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = false,
            alwaysVisibleInLobby = false,
            privateInvite = true
        )

        pvpGameService.createGame(userId1, request)
        pvpGameService.createGame(userId1, request)
        pvpGameService.createGame(userId1, request)

        assertEquals(3, countGameByStatus(CREATED))

        val e = assertFailsWith<BadRequestException> {
            pvpGameService.createGame(userId1, request)
        }
        assertEquals("You already have 3 pending games with the same settings", e.message)

        assertEquals(3, countGameByStatus(CREATED))
    }

    /**
     * Limit is per time category: different category should allow new games, but different color should not.
     */
    @Test
    fun createGameLimitTest02() = runTest {
        val request = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = false,
            alwaysVisibleInLobby = false,
            privateInvite = true
        )

        pvpGameService.createGame(userId1, request)
        pvpGameService.createGame(userId1, request)
        pvpGameService.createGame(userId1, request)

        // different color but same time category: should still be rejected
        assertFailsWith<BadRequestException> {
            pvpGameService.createGame(userId1, request.copy(inviterColor = BLACK))
        }

        // different time category (BLITZ vs RAPID): should succeed
        pvpGameService.createGame(userId1, request.copy(timeControlBase = 3.minutes.inWholeSeconds.toInt()))

        assertEquals(4, countGameByStatus(CREATED))
    }

    /**
     * Two Manchu games with compatible colors should be matched together.
     */
    @Test
    fun joinMatchingGameManchuTest01() = runTest {
        val request1 = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false,
            variant = Variant.MANCHU
        )

        val request2 = request1.copy(inviterColor = BLACK)

        val response1 = pvpGameService.createGame(userId1, request1)
        val response2 = pvpGameService.createGame(userId2, request2)

        assertEquals(CREATED, response1.eventType)
        assertEquals(RED, response1.color)

        assertEquals(JOINED, response2.eventType)
        assertEquals(BLACK, response2.color)

        assertEquals(1, countGameByStatus(JOINED))
        assertEquals(0, countGameByStatus(CREATED))
    }

    /**
     * Play a rated Manchu game to completion and verify that only the Manchu rating columns
     * are updated — Xiangqi ratings must remain at the default 1000.
     */
    @Test
    fun happyPathManchuTest01() = runTest {
        val gameId = createAndJoinGame(userId1, userId2, inviterColor = RED, variant = Variant.MANCHU)
        val manchuMoves = manchuGameMovesCache.listAll().random()

        manchuMoves.uciMoves.dropLast(1).forEachIndexed { i, move ->
            val result = pvpGameService.playMove(
                userId = userIdToPlay(gameId),
                request = PlayMoveRequest(gameId, move)
            )
            assertEquals(i + 1, result.updatedIndex)
            assertNull(result.gameEventType)
            assertNull(result.ratingUpdate)
        }

        val lastMoveResult = pvpGameService.playMove(
            userId = userIdToPlay(gameId),
            request = PlayMoveRequest(gameId, manchuMoves.uciMoves.last())
        )

        assertNotNull(lastMoveResult.gameEventType)
        val ratingUpdate = lastMoveResult.ratingUpdate!!
        assertEquals(true, ratingUpdate.isRated)
        assertEquals(1_000, ratingUpdate.inviterRatingFrom)
        assertEquals(1_000, ratingUpdate.inviteeRatingFrom)

        val gameData = pvpGameService.fetchGame(gameId)
        assertNotNull(gameData.outcome)
        assertNotEquals(Outcome.DRAW, gameData.outcome)

        val inviterColor = gameData.inviterColor!!
        val expectedWinnerRating = 1_000 + 8
        val expectedLoserRating = 1_000 - 8

        val inviterWins =
            (gameData.outcome == RED_WINS && inviterColor == RED) ||
                    (gameData.outcome == BLACK_WINS && inviterColor == BLACK)

        val inviteeWins =
            (gameData.outcome == RED_WINS && inviterColor == BLACK) ||
                    (gameData.outcome == BLACK_WINS && inviterColor == RED)

        when {
            inviterWins -> {
                assertEquals(expectedWinnerRating, ratingUpdate.inviterRatingTo)
                assertEquals(expectedLoserRating, ratingUpdate.inviteeRatingTo)
            }

            inviteeWins -> {
                assertEquals(expectedLoserRating, ratingUpdate.inviterRatingTo)
                assertEquals(expectedWinnerRating, ratingUpdate.inviteeRatingTo)
            }
        }

        // Xiangqi RAPID rating must remain unchanged at 1000
        assertEquals(1_000, fetchXiangqiRapidRating(userId1.id))
        assertEquals(1_000, fetchXiangqiRapidRating(userId2.id))

        // Manchu RAPID ratings must reflect the exact +8 / -8 change
        val manchuRating1 = fetchManchuRapidRating(userId1.id)
        val manchuRating2 = fetchManchuRapidRating(userId2.id)

        val userId1IsInviter = gameData.inviterId == userId1.id
        when {
            inviterWins -> {
                if (userId1IsInviter) {
                    assertEquals(expectedWinnerRating, manchuRating1)
                    assertEquals(expectedLoserRating, manchuRating2)
                } else {
                    assertEquals(expectedLoserRating, manchuRating1)
                    assertEquals(expectedWinnerRating, manchuRating2)
                }
            }

            inviteeWins -> {
                if (userId1IsInviter) {
                    assertEquals(expectedLoserRating, manchuRating1)
                    assertEquals(expectedWinnerRating, manchuRating2)
                } else {
                    assertEquals(expectedWinnerRating, manchuRating1)
                    assertEquals(expectedLoserRating, manchuRating2)
                }
            }
        }
    }


    private suspend fun userIdToPlay(gameId: String): String {
        val gameDataResponse = pvpGameService.fetchGame(gameId)
        val colorToPlay = colorToPlay(gameDataResponse.moveIndex)
        return if (gameDataResponse.inviterColor == colorToPlay)
            gameDataResponse.inviterId else gameDataResponse.inviteeId!!
    }

    private fun colorToPlay(currentHalfMoveIndex: Int) =
        if (currentHalfMoveIndex % 2 == 0) RED else BLACK

    private suspend fun countGameByStatus(status: GameEventType): Int =
        dslContext
            .selectCount()
            .from(GAME)
            .where(GAME.GAME_STATUS.eq(status))
            .awaitSingleValue()!!

    private suspend fun fetchAlwaysVisibleInLobby(gameId: String): Boolean =
        dslContext
            .select(GAME.ALWAYS_VISIBLE_IN_LOBBY)
            .from(GAME)
            .where(GAME.ID.eq(gameId))
            .awaitSingleValue()!!

    private suspend fun makeAlwaysVisibleInLobbyAllowed(userId: String) {
        confirmEmail(userId)
        setOpponentJoinedGameNotification(userId, enabled = true)
    }

    private suspend fun confirmEmail(userId: String) {
        val code = userDaoService.findById(userId)!!.emailConfirmationCode
        assertTrue(userService.confirmEmail(code))
    }

    private suspend fun setOpponentJoinedGameNotification(userId: String, enabled: Boolean) {
        userService.updateNotificationsSettings(
            userId,
            NotificationsSettingsDto(
                newsletter = false,
                opponentJoinedGame = enabled,
                opponentPlayedMove = false,
                opponentResigned = false,
                opponentProposedDraw = false,
                opponentAcceptedDraw = false,
                opponentDeclinedDraw = false,
            )
        )
    }

    private fun alwaysVisibleInLobbyRapidPublicGameRequest() =
        CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = true,
            privateInvite = false,
        )

    private suspend fun isOptionAlwaysVisibleInLobbyAllowed(userId: String): Boolean =
        pvpGameService.isOptionAlwaysVisibleInLobbyAllowed(userId)

    private suspend fun fetchXiangqiRapidRating(userId: String): Int =
        dslContext
            .select(USER.GAME_RATING_RAPID)
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleValue()!!

    private suspend fun fetchManchuRapidRating(userId: String): Int =
        dslContext
            .select(USER.GAME_RATING_MANCHU_RAPID)
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleValue()!!

    private suspend fun fetchEmailOf(userId: String): String =
        dslContext
            .select(USER.EMAIL)
            .from(USER)
            .where(USER.ID.eq(userId))
            .awaitSingleValue()!!

    private suspend fun setUserOffline(userId: String) =
        dslContext
            .update(USER)
            .set(USER.LAST_ONLINE, Clock.System.now().minus(1.hours))
            .where(USER.ID.eq(userId))
            .awaitExecute()

    private suspend fun setUserOnline(userId: String) =
        dslContext
            .update(USER)
            .set(USER.LAST_ONLINE, Clock.System.now())
            .where(USER.ID.eq(userId))
            .awaitExecute()

}
