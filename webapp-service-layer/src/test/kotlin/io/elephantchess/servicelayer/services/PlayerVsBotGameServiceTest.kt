package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.services.OpeningRepositoryCacheDaoService
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.engines.EnginePool
import io.elephantchess.model.Engine
import io.elephantchess.model.OpeningMode
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.servicelayer.dto.botgame.CreateBotGameRequest
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerVsBotGameServiceTest : ServiceTest() {

    private val pvbGameDaoService by inject<PlayerVsBotGameDaoService>()
    private val openingRepositoryDaoService by inject<OpeningRepositoryCacheDaoService>()
    private val userCache by inject<UserCache>()
    private val appConfig by inject<AppConfig>()
    private val refresherScope by inject<CoroutineScope>()
    private val mockEnginePool = mock<EnginePool>()

    private val service by lazy {
        PlayerVsBotGameService(
            enginesPool = mockEnginePool,
            pvbGameDaoService = pvbGameDaoService,
            openingRepositoryDaoService = openingRepositoryDaoService,
            userCache = userCache,
            appConfig = appConfig,
            refresherScope = refresherScope,
            logger = logger,
        )
    }

    @Test
    fun `manchu variant requires engine-only opening mode in service`() = runTest {
        val userId = UserId(AUTHENTICATED, signUpTestUser().second)

        val request = CreateBotGameRequest(
            color = RED,
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

}
