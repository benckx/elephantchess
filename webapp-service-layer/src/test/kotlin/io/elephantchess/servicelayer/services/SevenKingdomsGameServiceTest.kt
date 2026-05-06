package io.elephantchess.servicelayer.services

import io.elechantchess.sevenkingdoms.testutils.GameEntryCacheManager.randomGame
import io.elechantchess.sevenkingdoms.testutils.GameEntryDto
import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.records.SevenKingdomsGameEventRecord
import io.elephantchess.db.services.SevenKingdomsGameDaoService
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.userIdOfColor
import io.elephantchess.model.GameEventType.*
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.servicelayer.dto.sevenkingdoms.*
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.sevenkingdoms.ArmyCapturedEvent
import io.elephantchess.sevenkingdoms.Board
import io.elephantchess.sevenkingdoms.Color
import io.elephantchess.sevenkingdoms.Color.*
import io.elephantchess.sevenkingdoms.ExtraEliminationEvent
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.koin.core.component.inject
import kotlin.test.*

// TODO: also test cases where player play contiguous colors like PURPLE, BLACK and WHITE
// TODO: add winner_user_id next to winner_color in the game record -> add checks
class SevenKingdomsGameServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val gameService by inject<SevenKingdomsGameService>()
    private val gameDaoService by inject<SevenKingdomsGameDaoService>()

    private val userIds = mutableListOf<String>()

    // 2 players
    private val colorToUserMap2 by lazy {
        mapOf(
            WHITE to userIds[0],
            RED to userIds[0],
            ORANGE to userIds[0],
            BLUE to userIds[0],
            GREEN to userIds[1],
            PURPLE to userIds[1],
            BLACK to userIds[1]
        )
    }

    // 4 players
    private val colorToUserMap4 by lazy {
        mapOf(
            WHITE to userIds[0],
            RED to userIds[0],
            ORANGE to userIds[1],
            BLUE to userIds[1],
            GREEN to userIds[2],
            PURPLE to userIds[2],
            BLACK to userIds[3]
        )
    }

    // 7 players
    private val colorToUserMap7 by lazy {
        mapOf(
            WHITE to userIds[0],
            RED to userIds[1],
            ORANGE to userIds[2],
            BLUE to userIds[3],
            GREEN to userIds[4],
            PURPLE to userIds[5],
            BLACK to userIds[6]
        )
    }

    private fun colorToUserMap(numberOfPlayers: Int) =
        when (numberOfPlayers) {
            2 -> colorToUserMap2
            4 -> colorToUserMap4
            7 -> colorToUserMap7
            else -> throw IllegalArgumentException("Unsupported number of players: $numberOfPlayers")
        }

    @BeforeTest
    fun before() = runTest {
        userIds += (0..6).map { _ -> signUpTestUser().second }
        userService.refreshIsOnlineCache()
    }

    @AfterTest
    fun afterTest() = runTest {
        listOf(
            SEVEN_KINGDOMS_GAME_EVENT,
            SEVEN_KINGDOMS_GAME_MOVE,
            SEVEN_KINGDOMS_GAME
        ).forEach { table ->
            dslContext
                .deleteFrom(table)
                .awaitExecute()
        }
    }

    @Test
    fun createGameTest01() = runTest {
        val request = CreateGameRequest(7)
        gameService.createGame(request)

        // creating a second similar game raises an exception
        assertFailsWith<BadRequestException> {
            gameService.createGame(request)
        }

        gameService.createGame(request.copy(minPlayers = 2))
    }

    @Test
    fun `join minPlayer == 2 with 3 players (happy case)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId

        val response1 = joinGame(userIds[0], gameId, WHITE, RED)
        val response2 = joinGame(userIds[1], gameId, ORANGE, BLUE)

        var gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(CREATED, gameRecord.gameStatus)

        val response3 = joinGame(userIds[2], gameId, GREEN, PURPLE, BLACK)

        assertEquals(CREATED, response1.status)
        assertEquals(listOf(WHITE, RED), response1.colors)

        assertEquals(CREATED, response2.status)
        assertEquals(listOf(ORANGE, BLUE), response2.colors)

        assertEquals(JOINED, response3.status)
        assertEquals(listOf(GREEN, PURPLE, BLACK), response3.colors)

        gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(JOINED, gameRecord.gameStatus)
        assertEquals(userIds[0], gameRecord.playerWhite)
        assertEquals(userIds[0], gameRecord.playerRed)
        assertEquals(userIds[1], gameRecord.playerOrange)
        assertEquals(userIds[1], gameRecord.playerBlue)
        assertEquals(userIds[2], gameRecord.playerGreen)
        assertEquals(userIds[2], gameRecord.playerPurple)
        assertEquals(userIds[2], gameRecord.playerBlack)
    }

    @Test
    fun `join minPlayer == 2 with 4 players (happy case)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId

        val response1 = joinGame(userIds[0], gameId, WHITE, RED)
        val response2 = joinGame(userIds[1], gameId, ORANGE, BLUE)
        val response3 = joinGame(userIds[2], gameId, GREEN, PURPLE)
        val response4 = joinGame(userIds[3], gameId, BLACK)

        assertEquals(CREATED, response1.status)
        assertEquals(listOf(WHITE, RED), response1.colors)

        assertEquals(CREATED, response2.status)
        assertEquals(listOf(ORANGE, BLUE), response2.colors)

        assertEquals(CREATED, response3.status)
        assertEquals(listOf(GREEN, PURPLE), response3.colors)

        assertEquals(JOINED, response4.status)
        assertEquals(listOf(BLACK), response4.colors)

        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(JOINED, gameRecord.gameStatus)
        assertEquals(userIds[0], gameRecord.playerWhite)
        assertEquals(userIds[0], gameRecord.playerRed)
        assertEquals(userIds[1], gameRecord.playerOrange)
        assertEquals(userIds[1], gameRecord.playerBlue)
        assertEquals(userIds[2], gameRecord.playerGreen)
        assertEquals(userIds[2], gameRecord.playerPurple)
        assertEquals(userIds[3], gameRecord.playerBlack)
    }

    @Test
    fun `join minPlayer == 2 (happy case, multiple joins)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId

        val response1 = joinGame(userIds[0], gameId, WHITE)
        val response2 = joinGame(userIds[0], gameId, RED)
        val response3 = joinGame(userIds[0], gameId, ORANGE)

        assertEquals(listOf(WHITE), response1.colors)
        assertEquals(listOf(WHITE, RED), response2.colors)
        assertEquals(listOf(WHITE, RED, ORANGE), response3.colors)
    }

    @Test
    fun `join minPlayer == 2 with 7 players (happy path)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId

        // shuffle the colors because order doesn't matter,
        // as long as everybody picks a different color
        Color.entries.shuffled().forEachIndexed { i, color ->
            val response = joinGame(userIds[i], gameId, color)
            assertEquals(listOf(color), response.colors)
            if (i == 6) {
                assertEquals(JOINED, response.status)
            } else {
                assertEquals(CREATED, response.status)
            }
        }

        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(JOINED, gameRecord.gameStatus)
    }

    @Test
    fun `join minPlayer == 2 (fail when trying to use colors that are already picked)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId

        joinGame(userIds[0], gameId, WHITE, RED)

        val e = assertFailsWith<BadRequestException> {
            joinGame(userIds[1], gameId, RED, ORANGE, BLUE)
        }

        assertEquals("Color Red is already taken", e.message)
    }

    @Test
    fun `join minPlayer == 2 (non-contiguous colors error)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId

        val e = assertFailsWith<BadRequestException> {
            joinGame(userIds[0], gameId, WHITE, ORANGE)
        }

        assertEquals("Colors are not contiguous White, Orange", e.message)
    }

    @Test
    fun `join minPlayer == 2 (too many colors error)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId

        val e = assertFailsWith<BadRequestException> {
            joinGame(userIds[0], gameId, WHITE, RED, ORANGE, BLUE, GREEN)
        }

        assertEquals("You can only select up to 4 color(s) for this game", e.message)
    }

    @Test
    fun `join minPlayer == 2 (too many colors error, progressively)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId

        joinGame(userIds[0], gameId, WHITE, RED)
        joinGame(userIds[0], gameId, ORANGE, BLUE)

        val e = assertFailsWith<BadRequestException> {
            joinGame(userIds[0], gameId, GREEN)
        }

        assertEquals("You can only select up to 4 color(s) for this game", e.message)
    }

    @Test
    fun `join minPlayer == 4 with 4 players (happy case)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(4)).gameId

        val response1 = joinGame(userIds[0], gameId, WHITE, RED)
        val response2 = joinGame(userIds[1], gameId, ORANGE, BLUE)
        val response3 = joinGame(userIds[2], gameId, GREEN, PURPLE)
        val response4 = joinGame(userIds[3], gameId, BLACK)

        assertEquals(CREATED, response1.status)
        assertEquals(listOf(WHITE, RED), response1.colors)

        assertEquals(CREATED, response2.status)
        assertEquals(listOf(ORANGE, BLUE), response2.colors)

        assertEquals(CREATED, response3.status)
        assertEquals(listOf(GREEN, PURPLE), response3.colors)

        assertEquals(JOINED, response4.status)
        assertEquals(listOf(BLACK), response4.colors)

        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(JOINED, gameRecord.gameStatus)
        assertEquals(userIds[0], gameRecord.playerWhite)
        assertEquals(userIds[0], gameRecord.playerRed)
        assertEquals(userIds[1], gameRecord.playerOrange)
        assertEquals(userIds[1], gameRecord.playerBlue)
        assertEquals(userIds[2], gameRecord.playerGreen)
        assertEquals(userIds[2], gameRecord.playerPurple)
        assertEquals(userIds[3], gameRecord.playerBlack)
    }

    @Test
    fun `join minPlayer == 4 with 5 players (happy case)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(4)).gameId

        val response1 = joinGame(userIds[0], gameId, WHITE, RED)
        val response2 = joinGame(userIds[1], gameId, ORANGE, BLUE)
        val response3 = joinGame(userIds[2], gameId, GREEN, PURPLE)
        val response4 = joinGame(userIds[3], gameId, BLACK)

        assertEquals(CREATED, response1.status)
        assertEquals(listOf(WHITE, RED), response1.colors)

        assertEquals(CREATED, response2.status)
        assertEquals(listOf(ORANGE, BLUE), response2.colors)

        assertEquals(CREATED, response3.status)
        assertEquals(listOf(GREEN, PURPLE), response3.colors)

        assertEquals(JOINED, response4.status)
        assertEquals(listOf(BLACK), response4.colors)

        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(JOINED, gameRecord.gameStatus)
        assertEquals(userIds[0], gameRecord.playerWhite)
        assertEquals(userIds[0], gameRecord.playerRed)
        assertEquals(userIds[1], gameRecord.playerOrange)
        assertEquals(userIds[1], gameRecord.playerBlue)
        assertEquals(userIds[2], gameRecord.playerGreen)
        assertEquals(userIds[2], gameRecord.playerPurple)
        assertEquals(userIds[3], gameRecord.playerBlack)
    }

    @Test
    fun `join minPlayer == 4 (fail when trying to use colors that are already picked)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(4)).gameId

        joinGame(userIds[0], gameId, WHITE, RED)

        val e = assertFailsWith<BadRequestException> {
            joinGame(userIds[1], gameId, RED, ORANGE)
        }

        assertEquals("Color Red is already taken", e.message)
    }

    @Test
    fun `join minPlayer == 4 (non-contiguous colors error)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(4)).gameId

        val e = assertFailsWith<BadRequestException> {
            joinGame(userIds[0], gameId, WHITE, ORANGE)
        }

        assertEquals("Colors are not contiguous White, Orange", e.message)
    }

    @Test
    fun `join minPlayer == 4 (too many colors error)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(4)).gameId

        val e = assertFailsWith<BadRequestException> {
            joinGame(userIds[0], gameId, WHITE, RED, ORANGE)
        }

        assertEquals("You can only select up to 2 color(s) for this game", e.message)
    }

    @Test
    fun `join minPlayer == 7 with 7 players (happy case)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(7)).gameId

        val response1 = joinGame(userIds[0], gameId, WHITE)
        val response2 = joinGame(userIds[1], gameId, RED)
        val response3 = joinGame(userIds[2], gameId, ORANGE)
        val response4 = joinGame(userIds[3], gameId, BLUE)
        val response5 = joinGame(userIds[4], gameId, GREEN)
        val response6 = joinGame(userIds[5], gameId, PURPLE)
        val response7 = joinGame(userIds[6], gameId, BLACK)

        assertEquals(CREATED, response1.status)
        assertEquals(listOf(WHITE), response1.colors)

        assertEquals(CREATED, response2.status)
        assertEquals(listOf(RED), response2.colors)

        assertEquals(CREATED, response3.status)
        assertEquals(listOf(ORANGE), response3.colors)

        assertEquals(CREATED, response4.status)
        assertEquals(listOf(BLUE), response4.colors)

        assertEquals(CREATED, response5.status)
        assertEquals(listOf(GREEN), response5.colors)

        assertEquals(CREATED, response6.status)
        assertEquals(listOf(PURPLE), response6.colors)

        assertEquals(JOINED, response7.status)
        assertEquals(listOf(BLACK), response7.colors)

        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(JOINED, gameRecord.gameStatus)
        assertEquals(userIds[0], gameRecord.playerWhite)
        assertEquals(userIds[1], gameRecord.playerRed)
        assertEquals(userIds[2], gameRecord.playerOrange)
        assertEquals(userIds[3], gameRecord.playerBlue)
        assertEquals(userIds[4], gameRecord.playerGreen)
        assertEquals(userIds[5], gameRecord.playerPurple)
        assertEquals(userIds[6], gameRecord.playerBlack)
    }

    @Test
    fun `join minPlayer == 7 (fail when trying to use colors that are already picked)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(7)).gameId

        joinGame(userIds[0], gameId, WHITE)

        val e = assertFailsWith<BadRequestException> {
            joinGame(userIds[1], gameId, WHITE)
        }

        assertEquals("Color White is already taken", e.message)
    }

    @Test
    fun `join minPlayer == 7 (too many colors error)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(7)).gameId

        val e = assertFailsWith<BadRequestException> {
            joinGame(userIds[0], gameId, WHITE, RED)
        }

        assertEquals("You can only select up to 1 color(s) for this game", e.message)
    }

    @Test
    fun `cancel some colors (happy path)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId

        // join the game with multiple colors
        val joinResponse = joinGame(userIds[0], gameId, WHITE, RED, ORANGE)
        assertEquals(listOf(WHITE, RED, ORANGE), joinResponse.colors)
        assertEquals(CREATED, joinResponse.status)

        // cancel join for some of the colors
        val cancelResponse = gameService.cancelJoinGame(
            UserId(AUTHENTICATED, userIds[0]),
            CancelJoinGameRequest(gameId, listOf(RED, ORANGE))
        )
        assertEquals(listOf(WHITE), cancelResponse.colors)

        // verify the game record after cancellation
        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(CREATED, gameRecord.gameStatus)
        assertEquals(userIds[0], gameRecord.playerWhite)
        assertNull(gameRecord.playerRed)
        assertNull(gameRecord.playerOrange)
    }

    @Test
    fun `cancel join game (colors are not be contiguous error)`() = runTest {
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId

        joinGame(userIds[0], gameId, PURPLE, BLACK)

        // join the game with multiple colors
        val joinResponse = joinGame(userIds[1], gameId, WHITE, RED, ORANGE)
        assertEquals(listOf(WHITE, RED, ORANGE), joinResponse.colors)
        assertEquals(CREATED, joinResponse.status)

        // Attempt to cancel join for some of the colors, resulting in non-contiguous colors
        val e = assertFailsWith<BadRequestException> {
            gameService.cancelJoinGame(
                UserId(AUTHENTICATED, userIds[1]),
                CancelJoinGameRequest(gameId, listOf(RED))
            )
        }

        assertEquals("Colors are not contiguous White, Orange", e.message)

        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(CREATED, gameRecord.gameStatus)
        assertEquals(userIds[0], gameRecord.playerPurple)
        assertEquals(userIds[0], gameRecord.playerBlack)
        assertEquals(userIds[1], gameRecord.playerWhite)
        assertEquals(userIds[1], gameRecord.playerRed)
        assertEquals(userIds[1], gameRecord.playerOrange)
    }

    @Test
    fun `play game (minPlayer == 2, actual players == 2) (happy path)`() = runTest {
        // create and join the game
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId
        val responses = (0..1)
            .map { i -> userIds[i] }
            .map { userId -> joinGame(userId, gameId, *colorsOfUser(colorToUserMap2, userId)) }

        responses.dropLast(1).forEach { response -> assertEquals(CREATED, response.status) }
        assertEquals(JOINED, responses.last().status)

        // play moves
        val gameDto = randomGame()
        val moves = gameDto.moves
        val armyCapturedEvents = mutableListOf<ArmyCapturedEvent>()

        var colorToPlay: Color? = WHITE
        moves.forEachIndexed { i, move ->
            val userId = colorToUserMap2[colorToPlay]
            val request = PlayMoveRequest(gameId, move)
            val response = gameService.playMove(UserId(AUTHENTICATED, userId!!), request)
            colorToPlay = response.colorToPlay
            response.armyCapturedEvent?.let { event -> armyCapturedEvents += event }

            if (i == moves.size - 1) {
                verifyGameOver(response, gameDto)
            } else {
                verifyOngoingGame(response)
            }
        }

        // verify final game record
        verifyGameOverGameRecord(gameId, gameDto)

        // verify army captured events
        val board = gameDto.toBoard()
        assertEquals(board.listCaptureEvents(), armyCapturedEvents)
    }

    @Test
    fun `play game (minPlayer == 2, actual players == 4) (happy path)`() = runTest {
        // create and join the game
        val gameId = gameService.createGame(CreateGameRequest(2)).gameId
        val responses = (0..3)
            .map { i -> userIds[i] }
            .map { userId -> joinGame(userId, gameId, *colorsOfUser(colorToUserMap4, userId)) }

        responses.dropLast(1).forEach { response -> assertEquals(CREATED, response.status) }
        assertEquals(JOINED, responses.last().status)

        // play moves
        val gameDto = randomGame()
        val moves = gameDto.moves
        val armyCapturedEvents = mutableListOf<ArmyCapturedEvent>()

        var colorToPlay: Color? = WHITE
        moves.forEachIndexed { i, move ->
            val userId = colorToUserMap4[colorToPlay]
            val request = PlayMoveRequest(gameId, move)
            val response = gameService.playMove(UserId(AUTHENTICATED, userId!!), request)
            colorToPlay = response.colorToPlay
            response.armyCapturedEvent?.let { event -> armyCapturedEvents += event }

            if (i == moves.size - 1) {
                verifyGameOver(response, gameDto)
            } else {
                verifyOngoingGame(response)
            }
        }

        // verify final game record
        verifyGameOverGameRecord(gameId, gameDto)

        // verify army captured events
        val board = gameDto.toBoard()
        assertEquals(board.listCaptureEvents(), armyCapturedEvents)
    }

    @Test
    fun `play game (minPlayer == 7) (happy path)`() = runTest {
        // create and join the game
        val gameId = gameService.createGame(CreateGameRequest(7)).gameId
        val responses = (0..6)
            .map { i -> userIds[i] }
            .map { userId -> joinGame(userId, gameId, *colorsOfUser(colorToUserMap7, userId)) }

        responses.dropLast(1).forEach { response -> assertEquals(CREATED, response.status) }
        assertEquals(JOINED, responses.last().status)

        // play moves
        val gameDto = randomGame()
        val moves = gameDto.moves
        val armyCapturedEvents = mutableListOf<ArmyCapturedEvent>()

        var colorToPlay: Color? = WHITE
        moves.forEachIndexed { i, move ->
            val userId = colorToUserMap7[colorToPlay]
            val request = PlayMoveRequest(gameId, move)
            val response = gameService.playMove(UserId(AUTHENTICATED, userId!!), request)
            colorToPlay = response.colorToPlay
            response.armyCapturedEvent?.let { event -> armyCapturedEvents += event }

            if (i == moves.size - 1) {
                verifyGameOver(response, gameDto)
            } else {
                verifyOngoingGame(response)
            }
        }

        // verify final game record
        verifyGameOverGameRecord(gameId, gameDto)

        // verify army captured events
        val board = gameDto.toBoard()
        assertEquals(board.listCaptureEvents(), armyCapturedEvents)
    }

    @Test
    fun `play game (minPlayer == 2) (user not playing attempts to play error)`() = runTest {
        val (gameId, _, moves) = playSomeMovesFixture(numberOfPlayers = 2)
        val request = PlayMoveRequest(gameId, moves[10])
        val wrongUserId = userIds.last()
        val e = assertFailsWith<BadRequestException> {
            gameService.playMove(UserId(AUTHENTICATED, wrongUserId), request)
        }

        assertEquals("User $wrongUserId doesn't play in game $gameId", e.message)
    }

    @Test
    fun `play game (minPlayer == 7) (wrong user attempts to play error)`() = runTest {
        val (gameId, colorToPlay, moves) = playSomeMovesFixture()
        val request = PlayMoveRequest(gameId, moves[10])
        val wrongUserId = colorToUserMap7[colorToPlay.next()]!!
        val e = assertFailsWith<BadRequestException> {
            gameService.playMove(UserId(AUTHENTICATED, wrongUserId), request)
        }

        assertEquals("$wrongUserId is not allowed to play color $colorToPlay", e.message)
    }

    @Test
    fun `play game (minPlayer == 7) (wrong user attempts to play a color != colorToPlay)`() = runTest {
        val (gameId, colorToPlay, moves) = playSomeMovesFixture(numberOfMovesToTake = 10)
        val board = Board()
        board.registerMoves(moves.take(10))
        val moveForOtherColor = board
            .listAllLegalMoves()
            .filter { move -> board.pieceAt(move.from)!!.color != colorToPlay }
            .random()
        val incorrectColor = board.pieceAt(moveForOtherColor.from)!!.color
        val request = PlayMoveRequest(gameId, moveForOtherColor.uci)
        val userId = colorToUserMap7[colorToPlay]!!
        val e = assertFailsWith<BadRequestException> {
            gameService.playMove(UserId(AUTHENTICATED, userId), request)
        }

        // e.g.: Invalid move l17o14: It's not Purple's turn time to play, it's Blue
        val expected =
            "Invalid move ${moveForOtherColor.uci}: It's not ${incorrectColor}'s turn to play, it's $colorToPlay"
        assertEquals(expected, e.message!!)
    }

    @Test
    fun `play game (minPlayer == 7) (incorrect move error)`() = runTest {
        val (gameId, colorToPlay, _) = playSomeMovesFixture()
        val request = PlayMoveRequest(gameId, "foobar")
        val userId = colorToUserMap7[colorToPlay]!!
        val e = assertFailsWith<BadRequestException> {
            gameService.playMove(UserId(AUTHENTICATED, userId), request)
        }

        assertTrue(e.message!!.startsWith("Invalid move foobar"))
    }

    @Test
    fun `play game (minPlayer == 7) (incorrect gameId error)`() = runTest {
        val (_, colorToPlay, moves) = playSomeMovesFixture()
        val request = PlayMoveRequest("foobar", moves[10])
        val userId = colorToUserMap7[colorToPlay]!!
        val e = assertFailsWith<NotFoundException> {
            gameService.playMove(UserId(AUTHENTICATED, userId), request)
        }

        assertEquals("Game foobar not found", e.message)
    }

    @ParameterizedTest
    @ValueSource(ints = [4, 7])
    fun `resign of 1 player, game can continue (happy path)`(numberOfPlayers: Int) = runTest {
        val index = 10
        val (gameId, _, _) = playSomeMovesFixture(numberOfPlayers = numberOfPlayers, numberOfMovesToTake = index)
        val userId = userIds[2]

        gameService.resign(UserId(AUTHENTICATED, userId), ResignRequest(gameId))

        // check elimination events
        val eliminationEvents = fetchAllEvents(gameId).filter { it.eventType == RESIGNED }
        assertEquals(1, eliminationEvents.size)

        val event = eliminationEvents.first()
        assertEquals(index, event.index)

        val colorToUserMap = colorToUserMap(numberOfPlayers)
        val colorOfUsers = colorsOfUser(colorToUserMap, userId).toSet()
        val eliminatedColors = event.colors.map { Color.valueOf(it) }.toSet()
        assertEquals(colorOfUsers, eliminatedColors)

        // continue game for a while and ensure color don't come up again
        val board = Board()
        board.setExtraEliminationEvents(listOf(ExtraEliminationEvent(index, colorOfUsers.toList())))
        board.registerMoves(gameDaoService.fetchMovesUci(gameId))

        repeat(30) {
            val move = board.listCurrentLegalMoves().random().uci
            val request = PlayMoveRequest(gameId, move)
            val response = gameService.playMove(UserId(AUTHENTICATED, (colorToUserMap)[board.colorToPlay()]!!), request)
            board.registerMove(move)

            if (colorOfUsers.contains(response.colorToPlay!!)) {
                fail("Color ${response.colorToPlay} should have been eliminated")
            }
        }

        // check status
        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(JOINED, gameRecord.gameStatus)
    }

    @Test
    fun `resign of all players but one leads to victory by default (2 players)(happy path)(1)`() = runTest {
        val index = 10
        val (gameId, _, _) = playSomeMovesFixture(numberOfPlayers = 2, numberOfMovesToTake = index)

        `resign of all players but one leads to victory by default (2 players)`(
            gameId,
            userIds[0],
            userIds[1]
        )
    }

    @Test
    fun `resign of all players but one leads to victory by default (2 players)(happy path)(2)`() = runTest {
        val index = 10
        val (gameId, _, _) = playSomeMovesFixture(numberOfPlayers = 2, numberOfMovesToTake = index)

        `resign of all players but one leads to victory by default (2 players)`(
            gameId,
            userIds[1],
            userIds[0]
        )
    }

    private suspend fun `resign of all players but one leads to victory by default (2 players)`(
        gameId: String,
        loserUserId: String,
        winnerUserId: String
    ) {
        gameService.resign(UserId(AUTHENTICATED, loserUserId), ResignRequest(gameId))
        assertEquals(OTHER_VICTORY, gameDaoService.fetchGame(gameId)!!.gameStatus)

        // check events
        val allEvents = fetchAllEvents(gameId)

        val events = allEvents.filter { it.eventType == RESIGNED }
        assertEquals(1, events.size)
        assertEquals(loserUserId, events.first().userId)

        assertEquals(1, allEvents.filter { it.eventType == OTHER_VICTORY }.size)
        assertEquals(winnerUserId, allEvents.filter { it.eventType == OTHER_VICTORY }.map { it.userId }.first())

        // check status
        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(OTHER_VICTORY, gameRecord.gameStatus)
        assertEquals(winnerUserId, gameRecord.winnerUserId)
    }

    @Test
    fun `resign of all players but one leads to victory by default (4 players)(happy path)`() = runTest {
        val index = 10
        val (gameId, _, _) = playSomeMovesFixture(numberOfPlayers = 4, numberOfMovesToTake = index)

        gameService.resign(UserId(AUTHENTICATED, userIds[0]), ResignRequest(gameId))
        assertEquals(JOINED, gameDaoService.fetchGame(gameId)!!.gameStatus)

        gameService.resign(UserId(AUTHENTICATED, userIds[1]), ResignRequest(gameId))
        assertEquals(JOINED, gameDaoService.fetchGame(gameId)!!.gameStatus)

        gameService.resign(UserId(AUTHENTICATED, userIds[2]), ResignRequest(gameId))
        assertEquals(OTHER_VICTORY, gameDaoService.fetchGame(gameId)!!.gameStatus)

        // check elimination events
        val events = fetchAllEvents(gameId).filter { it.eventType == RESIGNED }
        assertEquals(3, events.size)

        // check status
        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(OTHER_VICTORY, gameRecord.gameStatus)
        assertEquals(userIds[3], gameRecord.winnerUserId)
    }

    private data class PlaySomeMoveFixture(
        val gameId: String,
        val colorToPlay: Color,
        val moves: List<String>
    )

    private suspend fun playSomeMovesFixture(
        numberOfPlayers: Int = 7,
        numberOfMovesToTake: Int = 10
    ): PlaySomeMoveFixture {
        // create and join the game
        val gameId = gameService.createGame(CreateGameRequest(numberOfPlayers)).gameId
        val colorToUserMap = colorToUserMap(numberOfPlayers)

        (0..<numberOfPlayers)
            .map { i -> userIds[i] }
            .map { userId -> joinGame(userId, gameId, *colorsOfUser(colorToUserMap, userId)) }

        // play moves
        val gameDto = randomGame()
        val moves = gameDto.moves

        var colorToPlay: Color? = WHITE
        moves.take(numberOfMovesToTake).forEachIndexed { _, move ->
            val userId = colorToUserMap[colorToPlay]
            val request = PlayMoveRequest(gameId, move)
            val response = gameService.playMove(UserId(AUTHENTICATED, userId!!), request)
            colorToPlay = response.colorToPlay
        }

        return PlaySomeMoveFixture(gameId, colorToPlay!!, moves)
    }

    private fun colorsOfUser(colorToUserMap: Map<Color, String>, userId: String): Array<Color> {
        return colorToUserMap.entries.filter { it.value == userId }.map { it.key }.toTypedArray()
    }

    private suspend fun joinGame(userId: String, gameId: String, vararg colors: Color): JoinGameResponse {
        return gameService.joinGame(
            UserId(AUTHENTICATED, userId),
            JoinGameRequest(gameId, colors.toList())
        )
    }

    private suspend fun fetchAllEvents(gameId: String): List<SevenKingdomsGameEventRecord> =
        dslContext
            .selectFrom(SEVEN_KINGDOMS_GAME_EVENT)
            .where(SEVEN_KINGDOMS_GAME_EVENT.GAME_ID.eq(gameId))
            .awaitMappedRecords()

    private fun verifyOngoingGame(response: PlayMoveResponse) {
        assertNotNull(response.colorToPlay)
        assertNull(response.statusUpdate)
        assertNull(response.winner)
        assertNull(response.victoryType)
    }

    private fun verifyGameOver(response: PlayMoveResponse, gameDto: GameEntryDto) {
        assertNull(response.colorToPlay)
        assertNotNull(response.statusUpdate)
        assertNotNull(response.winner)
        assertNotNull(response.victoryType)

        assertEquals(OTHER_VICTORY, response.statusUpdate)
        assertEquals(gameDto.winner, response.winner)
        assertEquals(gameDto.victoryType, response.victoryType)
    }

    private suspend fun verifyGameOverGameRecord(gameId: String, gameDto: GameEntryDto) {
        val gameRecord = gameDaoService.fetchGame(gameId)!!
        assertEquals(OTHER_VICTORY, gameRecord.gameStatus)
        assertEquals(gameDto.winner, gameRecord.winnerColor)
        assertEquals(gameRecord.userIdOfColor(gameRecord.winnerColor), gameRecord.winnerUserId)
        assertEquals(gameDto.moves.size, gameRecord.currentIndex)
        assertEquals(gameDto.toBoard().outputFen(), gameRecord.currentFen)
    }

}
