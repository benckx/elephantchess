package io.elephantchess.servicelayer.services

import io.elephantchess.config.ArgConfig
import io.elephantchess.config.DbConfig
import io.elephantchess.db.utils.getDslContext
import io.elephantchess.model.TimeControlMode
import io.elephantchess.servicelayer.dto.user.SignUpRequest
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.dto.game.JoinGameRequest
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.serviceLayerModule
import io.elephantchess.xiangqi.testutils.GameMovesDtoCache
import io.elephantchess.xiangqi.testutils.ManchuGameMovesDtoCache
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.Variant
import io.github.oshai.kotlinlogging.KotlinLogging
import liquibase.resource.ClassLoaderResourceAccessor
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils
import kotlin.time.Duration.Companion.minutes

abstract class ServiceTest : PostgresTest(), KoinComponent {

    protected val logger = KotlinLogging.logger {}
    protected val gameMovesCache by lazy { GameMovesDtoCache() }
    protected val manchuGameMovesCache by lazy { ManchuGameMovesDtoCache() }
    protected val userService by inject<UserService>()
    protected val pvpGameService by inject<PlayerVsPlayerGameService>()

    @BeforeAll
    override fun beforeAll() {
        super.beforeAll()
        startKoin {
            modules(
                serviceLayerModule(
                    argConfig = ArgConfig("local", null),
                    dslBuilder = {
                        val dbConfig = DbConfig(
                            dbName = "postgres",
                            url = container.jdbcUrl,
                            user = "postgres",
                            password = "postgres",
                        )

                        getDslContext(
                            dbConfig = dbConfig,
                            resourceAccessor = ClassLoaderResourceAccessor()
                        )
                    }
                )
            )
        }
    }

    @AfterAll
    override fun afterAll() {
        stopKoin()
        super.afterAll()
    }

    protected suspend fun signUpTestUser(i: Int = RandomUtils.nextInt(1_000, 1_000_000)): Pair<SignUpRequest, String> {
        val password = randomAlphanumeric(10)
        val request = SignUpRequest("test$i", "test$i@gmail.com", password)
        val either = userService.signUp(request)
        return request to either.right().userId
    }

    protected suspend fun createAndJoinGame(
        inviter: UserId,
        invitee: UserId,
        inviterColor: Color = RED,
        isRated: Boolean = true,
        timeControlBase: Int = 30.minutes.inWholeSeconds.toInt(),
        timeControlIncrement: Int? = null,
        timeControlMode: TimeControlMode = TimeControlMode.GAME_TIME,
        allowGuests: Boolean = true,
        alwaysVisibleInLobby: Boolean = false,
        privateInvite: Boolean = true,
        variant: Variant = Variant.XIANGQI,
    ): String {
        val response = pvpGameService.createGame(
            inviter,
            CreateGameRequest(
                inviterColor = inviterColor,
                isRated = isRated,
                timeControlBase = timeControlBase,
                timeControlIncrement = timeControlIncrement,
                timeControlMode = timeControlMode,
                allowGuests = allowGuests,
                alwaysVisibleInLobby = alwaysVisibleInLobby,
                privateInvite = privateInvite,
                variant = variant,
            )
        )

        pvpGameService.joinGame(invitee, JoinGameRequest(response.gameId))
        return response.gameId
    }

}
