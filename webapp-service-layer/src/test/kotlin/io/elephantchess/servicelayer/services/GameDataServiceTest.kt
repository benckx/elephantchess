package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_MOVE
import io.elephantchess.db.dao.codegen.Tables.GAME_STATUS_EVENT
import io.elephantchess.db.dao.codegen.Tables.MOVE_ANALYSIS
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.services.MoveAnalysisDaoService
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.ReferenceEventDaoService
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.services.ReferencePlayerDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.engines.EnginePool
import io.elephantchess.model.GameId
import io.elephantchess.model.GameType.PVP
import io.elephantchess.model.TimeControlMode
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.xiangqi.Color.RED
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import org.mockito.kotlin.mock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class GameDataServiceTest : ServiceTest() {

    private val appConfig by inject<AppConfig>()
    private val databaseService by inject<DatabaseService>()
    private val dslContext by inject<DSLContext>()
    private val engineCacheService by inject<EngineCacheService>()
    private val moveAnalysisDaoService by inject<MoveAnalysisDaoService>()
    private val pvbGameDaoService by inject<PlayerVsBotGameDaoService>()
    private val pvpGameService by inject<PlayerVsPlayerGameService>()
    private val pvpGameDaoService by inject<PlayerVsPlayerGameDaoService>()
    private val referenceEventDaoService by inject<ReferenceEventDaoService>()
    private val referenceGameDaoService by inject<ReferenceGameDaoService>()
    private val referencePlayerDaoService by inject<ReferencePlayerDaoService>()
    private val userCache by inject<UserCache>()
    private val userDaoService by inject<UserDaoService>()

    private lateinit var gameDataService: GameDataService
    private lateinit var userId: UserId

    @BeforeTest
    fun before() = runTest {
        userId = UserId(AUTHENTICATED, signUpTestUser().second)
        gameDataService = GameDataService(
            appConfig = appConfig,
            enginesPool = mock<EnginePool>(),
            databaseService = databaseService,
            engineCacheService = engineCacheService,
            moveAnalysisDaoService = moveAnalysisDaoService,
            pvbGameDaoService = pvbGameDaoService,
            pvpGameDaoService = pvpGameDaoService,
            referenceGameDaoService = referenceGameDaoService,
            referencePlayerDaoService = referencePlayerDaoService,
            referenceEventDaoService = referenceEventDaoService,
            userDaoService = userDaoService,
            userService = userService,
            userCache = userCache,
            logger = logger
        )
    }

    @AfterTest
    fun afterTest() = runTest {
        listOf(MOVE_ANALYSIS, GAME_MOVE, GAME_STATUS_EVENT, GAME, USER)
            .forEach { table ->
                dslContext
                    .deleteFrom(table)
                    .awaitExecute()
            }
    }

    @Test
    fun startGameAnalysisShouldRejectGameWithoutMoves() = runTest {
        val request = CreateGameRequest(
            inviterColor = RED,
            isRated = true,
            timeControlBase = 30.minutes.inWholeSeconds.toInt(),
            timeControlIncrement = null,
            timeControlMode = TimeControlMode.GAME_TIME,
            allowGuests = true,
            alwaysVisibleInLobby = false,
            privateInvite = false
        )

        val response = pvpGameService.createGame(userId, request)

        val exception = assertFailsWith<BadRequestException> {
            gameDataService.startGameAnalysis(GameId(PVP, response.gameId))
        }

        assertEquals("Game must contain at least one move to analyze", exception.message)
    }
}
