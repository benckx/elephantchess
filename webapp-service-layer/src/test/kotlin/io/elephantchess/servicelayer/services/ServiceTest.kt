package io.elephantchess.servicelayer.services

import io.elephantchess.config.ArgConfig
import io.elephantchess.config.DbConfig
import io.elephantchess.db.utils.getDslContext
import io.elephantchess.engines.EnginePool
import io.elephantchess.servicelayer.dto.user.SignUpRequest
import io.elephantchess.servicelayer.serviceLayerModule
import io.elephantchess.xiangqi.testutils.GameMovesDtoCache
import io.elephantchess.xiangqi.testutils.ManchuGameMovesDtoCache
import io.github.oshai.kotlinlogging.KotlinLogging
import liquibase.resource.ClassLoaderResourceAccessor
import org.apache.commons.lang3.RandomStringUtils.insecure
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils

abstract class ServiceTest : PostgresTest(), KoinComponent {

    protected val logger = KotlinLogging.logger {}
    protected val gameMovesCache by lazy { GameMovesDtoCache() }
    protected val manchuGameMovesCache by lazy { ManchuGameMovesDtoCache() }
    protected val userService by inject<UserService>()
    protected open val enginesPool: EnginePool? = null

    @BeforeAll
    override fun beforeAll() {
        super.beforeAll()
        startKoin {
            modules(
                serviceLayerModule(
                    argConfig = ArgConfig("local", null),
                    enginesPool = enginesPool,
                    dslBuilder = {
                        val dbConfig = DbConfig(
                            dbName = "postgres",
                            url = container.jdbcUrl,
                            user = dbUser,
                            password = dbPassword,
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

    suspend fun signUpTestUser(
        i: Int = RandomUtils.nextInt(1_000, 1_000_000),
        transferGuestData: Boolean = false,
        guestUserId: String? = null,
    ): Pair<SignUpRequest, String> {
        val password = insecure().nextAlphanumeric(10)
        val request = SignUpRequest(
            username = "test$i",
            email = "test$i@gmail.com",
            password = password,
            transferGuestData = transferGuestData,
        )
        val either = userService.signUp(request, guestUserId = guestUserId)
        return request to either.right().userId
    }

}
