package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.services.OpeningRepositoryCacheDaoService
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.protocol.model.InfoLinesResult
import io.elephantchess.model.Engine
import io.elephantchess.model.GameEventType
import io.elephantchess.model.OpeningMode
import io.elephantchess.model.Outcome
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.model.UserType.GUEST
import io.elephantchess.servicelayer.dto.botgame.CreateBotGameRequest
import io.elephantchess.servicelayer.dto.botgame.PlayMoveBotGameRequest
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.exceptions.RequestTimeoutException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.utils.TryEither
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Color.WHITE
import io.elephantchess.xiangqi.JanggiBoard
import io.elephantchess.xiangqi.JanggiSetup
import io.elephantchess.xiangqi.Variant
import io.elephantchess.xiangqi.janggiStartFen
import io.elephantchess.xiangqi.testutils.JanggiGameMovesDto
import io.elephantchess.xiangqi.testutils.JanggiGameMovesDtoCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.koin.core.component.inject
import org.mockito.kotlin.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlayerVsBotGameServiceTest : ServiceTest() {

    private val pvbGameDaoService by inject<PlayerVsBotGameDaoService>()
    private val openingRepositoryDaoService by inject<OpeningRepositoryCacheDaoService>()
    private val userCache by inject<UserCache>()
    private val exceptionService by inject<ExceptionService>()
    private val appConfig by inject<AppConfig>()
    private val refresherScope by inject<CoroutineScope>()
    private val mockEnginePool = mock<EnginePool>()

    @BeforeEach
    fun resetEngine() {
        reset(mockEnginePool)
    }

    // mocked EnginePool
    private val service by lazy {
        PlayerVsBotGameService(
            enginesPool = mockEnginePool,
            pvbGameDaoService = pvbGameDaoService,
            openingRepositoryDaoService = openingRepositoryDaoService,
            userCache = userCache,
            exceptionService = exceptionService,
            appConfig = appConfig,
            refresherScope = refresherScope,
            logger = logger,
        )
    }

    private val janggiDrawFixture by lazy {
        fun stalemateIndex(game: JanggiGameMovesDto): Int {
            val board = JanggiBoard(janggiStartFen(JanggiSetup.valueOf(game.blueSetup), JanggiSetup.valueOf(game.redSetup)))
            return game.uciMoves.indexOfFirst { uci ->
                board.registerMove(uci)
                !board.isDraw() && !board.isInCheck(board.colorToPlay()) &&
                    board.listLegalMoves(board.colorToPlay()).none { it.from != it.to }
            }
        }
        val game = JanggiGameMovesDtoCache().randomGame {
            val index = stalemateIndex(it)
            // PvB excludes passes, so the human move immediately before the bot's final move must not be a pass.
            index >= 1 && it.uciMoves[index - 1].let { move -> move.take(2) != move.takeLast(2) }
        }
        game to stalemateIndex(game)
    }

    private fun fixtureBoard(movesBeforeEnd: Int): JanggiBoard {
        val (fixture, finalIndex) = janggiDrawFixture
        val startFen = janggiStartFen(JanggiSetup.valueOf(fixture.blueSetup), JanggiSetup.valueOf(fixture.redSetup))
        return JanggiBoard(startFen).apply {
            fixture.uciMoves.take(finalIndex + 1 - movesBeforeEnd).forEach { registerMove(it) }
        }
    }

    private fun fixtureMove(movesBeforeEnd: Int): String {
        val (fixture, finalIndex) = janggiDrawFixture
        return fixture.uciMoves[finalIndex + 1 - movesBeforeEnd]
    }

    private suspend fun createGame(userId: UserId, fen: String, color: Color = WHITE, variant: Variant = Variant.JANGGI): String =
        service.create(
            userId,
            CreateBotGameRequest(
                color = color,
                depth = 1,
                engine = Engine.FAIRYSTOCKFISH,
                startFen = fen,
                openingMode = OpeningMode.ENGINE_ONLY,
                variant = variant,
            ),
        ).gameId

    private suspend fun stubBotMove(move: String) {
        whenever(mockEnginePool.queryForDepth(any(), any(), any(), any(), any(), anyOrNull(), any(), any()))
            .thenReturn(InfoLinesResult(emptyList(), false, move, null))
    }

    @Test
    fun `Janggi fixture ends in a persisted draw after the human move without querying the engine`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val board = fixtureBoard(1)
            val finalMove = fixtureMove(1)
            val gameId = createGame(userId, board.outputFen(), board.colorToPlay())
            val response = service.playMove(userId.id, PlayMoveBotGameRequest(gameId, finalMove))

            assertEquals(GameEventType.STALEMATED, response.statusUpdate)
            assertEquals(Outcome.DRAW, response.outcome)
            assertEquals(null, response.botMove)
            assertEquals(1, response.position)
            val saved = pvbGameDaoService.fetchById(gameId)!!
            assertEquals(Outcome.DRAW, saved.outcome)
            assertEquals(GameEventType.STALEMATED, saved.gameStatus)
            assertEquals(response.fen, saved.currentFen)
            assertEquals(listOf(finalMove), pvbGameDaoService.listMoves(gameId))
            verifyNoInteractions(mockEnginePool)
            assertFailsWith<BadRequestException> {
                service.playMove(userId.id, PlayMoveBotGameRequest(gameId, finalMove))
            }
        }

    @Test
    fun `Janggi draws after the bot move and excludes passes from the engine search`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val board = fixtureBoard(2)
            val humanMove = fixtureMove(2)
            val finalMove = fixtureMove(1)
            stubBotMove(finalMove)
            val gameId = createGame(userId, board.outputFen(), board.colorToPlay())
            val response = service.playMove(userId.id, PlayMoveBotGameRequest(gameId, humanMove))

            assertEquals(Outcome.DRAW, response.outcome)
            assertEquals(GameEventType.STALEMATED, response.statusUpdate)
            assertEquals(finalMove, response.botMove)
            assertEquals(2, response.position)
            assertEquals(Outcome.DRAW, pvbGameDaoService.fetchById(gameId)!!.outcome)
            assertEquals(listOf(humanMove, finalMove), pvbGameDaoService.listMoves(gameId))
            val searchMoves = argumentCaptor<List<String>>()
            verify(mockEnginePool).queryForDepth(any(), any(), any(), any(), eq(Variant.JANGGI), searchMoves.capture(), any(), any())
            assertTrue(searchMoves.firstValue.isNotEmpty())
            assertFalse(searchMoves.firstValue.any { it.take(2) == it.takeLast(2) })
        }

    @Test
    fun `Janggi draws when the first bot move stalemates the human`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val board = fixtureBoard(1)
            val finalMove = fixtureMove(1)
            stubBotMove(finalMove)
            val gameId = createGame(userId, board.outputFen(), board.colorToPlay().reverse())
            val game = pvbGameDaoService.fetchById(gameId)!!
            assertEquals(GameEventType.STALEMATED, game.gameStatus)
            assertEquals(Outcome.DRAW, game.outcome)
            assertEquals(1, game.currentHalfMoveIndex)
            assertEquals(listOf(finalMove), pvbGameDaoService.listMoves(gameId))
        }

    @Test
    fun `Janggi rejects a start position with no non-pass legal moves`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val exception = assertFailsWith<BadRequestException> {
                createGame(userId, fixtureBoard(0).outputFen(), fixtureBoard(0).colorToPlay())
            }
            assertEquals("Start position is stalemate", exception.message)
            verifyNoInteractions(mockEnginePool)
        }

    @Test
    fun `Janggi rejects human and bot passes without persisting moves`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val gameId = createGame(userId, JanggiBoard.DEFAULT_JANGGI_START_FEN)
            assertFailsWith<BadRequestException> {
                service.playMove(userId.id, PlayMoveBotGameRequest(gameId, "e1e1"))
            }
            verifyNoInteractions(mockEnginePool)
            stubBotMove("e8e8")
            assertFailsWith<RequestTimeoutException> {
                service.playMove(userId.id, PlayMoveBotGameRequest(gameId, "e3d3"))
            }
            assertTrue(pvbGameDaoService.listMoves(gameId).isEmpty())
            assertEquals(0, pvbGameDaoService.fetchById(gameId)!!.currentHalfMoveIndex)
        }

    @Test
    fun `failed callbacks return TryEither without persisting moves or changing the game`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val gameId = createGame(userId, JanggiBoard.DEFAULT_JANGGI_START_FEN)
            val failure = IllegalStateException("callback failed")
            val result = pvbGameDaoService.saveUserMoveResult(gameId, "e3d3") { _, _ -> throw failure }
            assertSame(failure, assertIs<TryEither.Invalid<*>>(result).exception)
            assertTrue(pvbGameDaoService.listMoves(gameId).isEmpty())
            val game = pvbGameDaoService.fetchById(gameId)!!
            assertEquals(0, game.currentHalfMoveIndex)
            assertEquals(JanggiBoard.DEFAULT_JANGGI_START_FEN, game.currentFen)
            assertEquals(GameEventType.CREATED, game.gameStatus)
        }

    @Test
    fun `ordinary moves still persist both moves and engine timeouts persist neither`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val gameId = createGame(userId, JanggiBoard.DEFAULT_JANGGI_START_FEN)
            val timeout = assertFailsWith<RequestTimeoutException> {
                service.playMove(userId.id, PlayMoveBotGameRequest(gameId, "e3d3"))
            }
            assertEquals("Engine did not respond in time, reload page", timeout.message)
            assertTrue(pvbGameDaoService.listMoves(gameId).isEmpty())

            val illegal = assertFailsWith<BadRequestException> {
                service.playMove(userId.id, PlayMoveBotGameRequest(gameId, "not-a-move"))
            }
            assertEquals("Illegal move not-a-move", illegal.message)
            stubBotMove("a6b6")
            val response = service.playMove(userId.id, PlayMoveBotGameRequest(gameId, "e3d3"))
            assertEquals(null, response.statusUpdate)
            assertEquals(null, response.outcome)
            assertEquals(2, response.position)
            assertEquals(listOf("e3d3", "a6b6"), pvbGameDaoService.listMoves(gameId))
            assertEquals(response.fen, pvbGameDaoService.fetchById(gameId)!!.currentFen)
        }

    @Test
    fun `Xiangqi stalemate remains a victory and checkmate wins in both variants`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val fen = "4k4/9/R8/3R1R3/9/9/9/9/9/5K3 w - - 0 1"
            val xiangqi = createGame(userId, fen, variant = Variant.XIANGQI)
            val stalemate = service.playMove(userId.id, PlayMoveBotGameRequest(xiangqi, "a7a8"))
            assertEquals(GameEventType.STALEMATED, stalemate.statusUpdate)
            assertEquals(Outcome.WHITE_WINS, stalemate.outcome)
            for (variant in listOf(Variant.XIANGQI, Variant.JANGGI)) {
                val gameId = createGame(userId, fen, variant = variant)
                val mate = service.playMove(userId.id, PlayMoveBotGameRequest(gameId, "a7e7"))
                assertEquals(GameEventType.CHECKMATED, mate.statusUpdate)
                assertEquals(Outcome.WHITE_WINS, mate.outcome)
            }
            verifyNoInteractions(mockEnginePool)
        }

    @Test
    fun `can create a Janggi bot game with Fairy Stockfish`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val startFen = janggiStartFen(JanggiSetup.CENTRAL_CHARIOT, JanggiSetup.OUTER_ELEPHANT)
            val response = service.create(
                userId,
                CreateBotGameRequest(
                    color = WHITE,
                    depth = 5,
                    engine = Engine.FAIRYSTOCKFISH,
                    startFen = startFen,
                    openingMode = OpeningMode.ENGINE_ONLY,
                    variant = Variant.JANGGI,
                ),
            )

            val game = pvbGameDaoService.fetchById(response.gameId)!!
            assertEquals(Variant.JANGGI, game.variant)
            assertEquals(startFen, game.startFen)
            assertEquals(Engine.FAIRYSTOCKFISH, game.engine)
        }

    @Test
    fun `Janggi requires Fairy Stockfish`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val request = CreateBotGameRequest(
                color = WHITE,
                depth = 5,
                engine = Engine.PIKAFISH,
                startFen = null,
                openingMode = OpeningMode.ENGINE_ONLY,
                variant = Variant.JANGGI,
            )
            val exception = assertFailsWith<BadRequestException> {
                service.create(userId, request)
            }
            assertEquals("Pikafish does not support the Janggi variant. Please use Fairy Stockfish", exception.message)
        }

    @Test
    fun `Janggi requires engine-only opening mode`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)
            val request = CreateBotGameRequest(
                color = WHITE,
                depth = 5,
                engine = Engine.FAIRYSTOCKFISH,
                startFen = null,
                openingMode = OpeningMode.BY_FREQUENCY,
                variant = Variant.JANGGI,
            )
            val exception = assertFailsWith<BadRequestException> {
                service.create(userId, request)
            }
            assertEquals("Variants require engine-only opening mode", exception.message)
        }

    @Test
    fun `manchu variant requires engine-only opening mode in service`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)

            val request = CreateBotGameRequest(
                color = WHITE,
                depth = 5,
                engine = Engine.FAIRYSTOCKFISH,
                startFen = null,
                openingMode = OpeningMode.BY_FREQUENCY,
                variant = Variant.MANCHU,
            )

            val exception = assertFailsWith<BadRequestException> {
                service.create(userId, request)
            }

            assertEquals("Variants require engine-only opening mode", exception.message)
        }

    @Test
    fun `manchu variant can not be played with Pikafish`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)

            val request = CreateBotGameRequest(
                color = WHITE,
                depth = 5,
                engine = Engine.PIKAFISH,
                startFen = null,
                openingMode = OpeningMode.ENGINE_ONLY,
                variant = Variant.MANCHU,
            )

            val exception = assertFailsWith<BadRequestException> {
                service.create(userId, request)
            }

            assertEquals("Pikafish does not support the Manchu variant. Please use Fairy Stockfish", exception.message)
        }

    @Test
    fun `can not create a game with more 14 in depth`() =
        runTest {
            val userId = UserId(AUTHENTICATED, signUpTestUser().second)

            val request = CreateBotGameRequest(
                color = WHITE,
                depth = 15,
                engine = Engine.PIKAFISH,
                startFen = null,
            )

            val exception = assertFailsWith<BadRequestException> {
                service.create(userId, request)
            }

            assertEquals("Depth must be between 1 and 14", exception.message)
        }

    @Test
    fun `guest can not create a game with depth greater than 6`() =
        runTest {
            val guestId = UserId(GUEST, userService.obtainGuestUserToken().id)

            val request = CreateBotGameRequest(
                color = WHITE,
                depth = 7,
                engine = Engine.PIKAFISH,
                startFen = null,
            )

            val exception = assertFailsWith<BadRequestException> {
                service.create(guestId, request)
            }

            assertEquals("You must be authenticated in to play with depth greater than 6", exception.message)
        }
}
