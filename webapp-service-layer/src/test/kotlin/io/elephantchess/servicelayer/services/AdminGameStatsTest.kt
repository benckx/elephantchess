package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.Tables.BOT_GAME
import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.engines.EnginePool
import io.elephantchess.model.Engine
import io.elephantchess.model.GameEventType
import io.elephantchess.model.OpeningMode
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.botgame.CreateBotGameRequest
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.admin.AdminOverviewService
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.test.runTest
import org.jooq.DSLContext
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class AdminGameStatsTest : ServiceTest() {

    private val dsl by inject<DSLContext>()
    private val pvbService by inject<PlayerVsBotGameService>()
    private val pvbDao by inject<PlayerVsBotGameDaoService>()
    private val overview by inject<AdminOverviewService>()
    private val global by inject<GlobalAnalyticsService>()

    @Test
    fun `activity filters and global counts preserve move thresholds and variants`() =
        runTest {
            loadKoinModules(module { single<EnginePool> { mock() } })
            val user = UserId(UserType.AUTHENTICATED, signUpTestUser().second)
            val opponent = UserId(UserType.AUTHENTICATED, signUpTestUser().second)
            val base = Instant.parse("2030-01-01T00:00:00Z").toEpochMilliseconds()

            suspend fun botGame(variant: Variant, engine: Engine, index: Int, offset: Long): String {
                val id = pvbService.create(
                    user,
                    CreateBotGameRequest(
                        color = Color.WHITE,
                        depth = 1,
                        engine = engine,
                        startFen = null,
                        openingMode = OpeningMode.ENGINE_ONLY,
                        variant = variant,
                    ),
                ).gameId
                dsl.update(BOT_GAME)
                    .set(BOT_GAME.CURRENT_HALF_MOVE_INDEX, index)
                    .set(BOT_GAME.LAST_UPDATED, Instant.fromEpochMilliseconds(base + offset))
                    .where(BOT_GAME.ID.eq(id))
                    .awaitExecute()
                return id
            }

            assertNull(overview.fetchLatestPvbActivity().latestJanggi3Activity)
            botGame(Variant.MANCHU, Engine.FAIRYSTOCKFISH, 6, 100)
            botGame(Variant.JANGGI, Engine.FAIRYSTOCKFISH, 6, 200)
            botGame(Variant.XIANGQI, Engine.FAIRYSTOCKFISH, 6, 300)
            botGame(Variant.XIANGQI, Engine.PIKAFISH, 6, 400)
            val shortJanggi = botGame(Variant.JANGGI, Engine.FAIRYSTOCKFISH, 5, 500)

            val activity = overview.fetchLatestPvbActivity()
            assertEquals(base + 500, activity.latestPvbActivity)
            assertEquals(base + 400, activity.latestPvb3Activity)
            assertEquals(base + 300, activity.latestFairy3Activity)
            assertEquals(base + 200, activity.latestJanggi3Activity)
            assertEquals(base + 100, activity.latestManchu3Activity)
            assertEquals(1, pvbDao.countVariantGames(6, Variant.JANGGI))

            dsl.update(BOT_GAME)
                .set(BOT_GAME.GAME_STATUS, GameEventType.STALEMATED)
                .where(BOT_GAME.ID.eq(shortJanggi))
                .awaitExecute()
            assertEquals(2, pvbDao.countVariantGames(6, Variant.JANGGI))
            assertEquals(base + 200, overview.fetchLatestPvbActivity().latestJanggi3Activity)

            val manchu = createAndJoinGame(user, opponent, variant = Variant.MANCHU)
            dsl.update(GAME)
                .set(GAME.CURRENT_HALF_MOVE_INDEX, 5)
                .set(GAME.LAST_UPDATED, Instant.fromEpochMilliseconds(base + 600))
                .where(GAME.ID.eq(manchu))
                .awaitExecute()
            assertNull(overview.fetchLatestPvpActivity().latestManchu3Activity)
            dsl.update(GAME)
                .set(GAME.CURRENT_HALF_MOVE_INDEX, 6)
                .where(GAME.ID.eq(manchu))
                .awaitExecute()
            assertEquals(base + 600, overview.fetchLatestPvpActivity().latestManchu3Activity)

            val stats = global.fetchGlobalGameStats()
            assertEquals(2, stats.totalJanggiGames)
            assertEquals(2, stats.totalManchuGames)
        }
}
