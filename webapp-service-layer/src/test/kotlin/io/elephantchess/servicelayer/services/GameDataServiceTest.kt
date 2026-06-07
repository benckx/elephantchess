package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_MOVE
import io.elephantchess.db.dao.codegen.Tables.GAME_STATUS_EVENT
import io.elephantchess.db.dao.codegen.Tables.MOVE_ANALYSIS
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.awaitExecute
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

    override val enginesPool: EnginePool = mock()

    private val dslContext by inject<DSLContext>()
    private val gameDataService by inject<GameDataService>()

    private lateinit var userId: UserId

    @BeforeTest
    fun before() = runTest {
        userId = UserId(AUTHENTICATED, signUpTestUser().second)
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
