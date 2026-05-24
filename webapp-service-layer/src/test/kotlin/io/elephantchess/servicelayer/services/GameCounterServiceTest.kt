package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.pojos.BotGame
import io.elephantchess.db.dao.codegen.tables.pojos.BotGameStatusEvent
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.model.Engine
import io.elephantchess.model.GameEventType.CREATED
import io.elephantchess.model.GameEventType.CHECKMATED
import io.elephantchess.model.GameEventType.STALEMATED
import io.elephantchess.model.Outcome.RED_WINS
import io.elephantchess.model.TimeControlMode
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.dto.game.JoinGameRequest
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.test.runTest
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class GameCounterServiceTest : ServiceTest() {

    private val dslContext by inject<DSLContext>()
    private val pvbGameDaoService by inject<PlayerVsBotGameDaoService>()
    private val pvpGameDaoService by inject<PlayerVsPlayerGameDaoService>()
    private val pvpGameService by inject<PlayerVsPlayerGameService>()

    private lateinit var userId1: UserId
    private lateinit var userId2: UserId

    @BeforeTest
    fun before() = runTest {
        userId1 = UserId(AUTHENTICATED, signUpTestUser().second)
        userId2 = UserId(AUTHENTICATED, signUpTestUser().second)
    }

    @AfterTest
    fun afterEach() = runTest {
        listOf(
            GAME_MOVE, GAME_STATUS_EVENT, GAME,
            BOT_GAME_MOVE, BOT_GAME_STATUS_EVENT, BOT_GAME,
            USER
        )
            .forEach { table ->
                dslContext
                    .deleteFrom(table)
                    .awaitExecute()
            }
    }

    @Test
    fun `bot game counters include short checkmated games`() = runTest {
        insertBotGame(CHECKMATED, Variant.MANCHU)
        insertBotGame(STALEMATED, Variant.XIANGQI)
        insertBotGame(CREATED, Variant.MANCHU)
        insertBotGame(CREATED, Variant.XIANGQI, GameDataService.MIN_MOVE_INDEX)

        assertEquals(3, pvbGameDaoService.countTotalGames(GameDataService.MIN_MOVE_INDEX))
        assertEquals(1, pvbGameDaoService.countManchuGames(GameDataService.MIN_MOVE_INDEX))
    }

    @Test
    fun `player game counters include short stalemated games`() = runTest {
        val countedGameId = createAndJoinGame(Variant.MANCHU)
        pvpGameDaoService.updateStatus(userId1.id, countedGameId, STALEMATED, RED_WINS)

        val xiangqiGameId = createAndJoinGame(Variant.XIANGQI)
        pvpGameDaoService.updateStatus(userId1.id, xiangqiGameId, CHECKMATED, RED_WINS)

        createAndJoinGame(Variant.MANCHU)
        val longXiangqiGameId = createAndJoinGame(Variant.XIANGQI)
        dslContext
            .update(GAME)
            .set(GAME.CURRENT_HALF_MOVE_INDEX, GameDataService.MIN_MOVE_INDEX)
            .where(GAME.ID.eq(longXiangqiGameId))
            .awaitExecute()

        assertEquals(3, pvpGameDaoService.countTotalGames(GameDataService.MIN_MOVE_INDEX))
        assertEquals(1, pvpGameDaoService.countManchuGames(GameDataService.MIN_MOVE_INDEX))
    }

    private suspend fun createAndJoinGame(variant: Variant): String {
        val gameId = pvpGameService.createGame(
            userId1,
            CreateGameRequest(
                inviterColor = RED,
                isRated = true,
                timeControlBase = 30.minutes.inWholeSeconds.toInt(),
                timeControlIncrement = null,
                timeControlMode = TimeControlMode.GAME_TIME,
                allowGuests = true,
                alwaysVisibleInLobby = false,
                privateInvite = false,
                variant = variant
            )
        ).gameId

        pvpGameService.joinGame(userId2, JoinGameRequest(gameId))

        return gameId
    }

    private suspend fun insertBotGame(
        status: io.elephantchess.model.GameEventType,
        variant: Variant,
        currentHalfMoveIndex: Int = 0,
    ) {
        val now = Clock.System.now()
        val gameId = randomAlphanumeric(12)
        val gameRecord = BotGame().apply {
            id = gameId
            userId = userId1.id
            userColor = RED
            engine = Engine.FAIRYSTOCKFISH
            engineVersion = "11.2"
            depth = 4
            startFen = null
            this.variant = variant
            gameStatus = status
            currentFen = DEFAULT_START_FEN
            this.currentHalfMoveIndex = currentHalfMoveIndex
            created = now
            lastUpdated = now
        }
        val statusRecord = BotGameStatusEvent().apply {
            botGameId = gameId
            eventType = status
            eventTime = now
        }

        pvbGameDaoService.insertGame(gameRecord, statusRecord)
    }
}
