package io.elephantchess.servicelayer.koin

import io.elephantchess.config.AppConfig
import io.elephantchess.config.ArgConfig
import io.elephantchess.config.DbConfig
import io.elephantchess.config.loadAppConfig
import io.elephantchess.db.utils.getDslContext
import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.EngineConfig
import io.elephantchess.engines.process.FairyStockfishEngineId
import io.elephantchess.engines.process.PikafishEngineId
import io.elephantchess.engines.protocol.commands.LocalProcessLocator
import io.elephantchess.servicelayer.services.MailTemplateRender
import io.elephantchess.servicelayer.services.resolvers.ContactLinkTagResolver
import io.elephantchess.servicelayer.services.resolvers.MailFragmentResolver
import io.elephantchess.servicelayer.services.resolvers.UpdateMailSettingsTagResolver
import io.elephantchess.servicelayer.utils.DockerizedProcessLocator
import io.elephantchess.servicelayer.utils.ops.registerInjectables
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jooq.DSLContext
import org.koin.dsl.module
import java.util.concurrent.Executors

private val kLogger = KotlinLogging.logger {}

fun serviceLayerModule(
    argConfig: ArgConfig,
    eagerAllowed: Boolean = false,
    dslBuilder: (DbConfig) -> DSLContext = { getDslContext(it) },
    enginesPool: EnginePool? = null,
) =
    module {
        val appConfig = loadAppConfig(argConfig)
        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single { appConfig }
        single { appConfig.dbConfig }
        single { dslBuilder(get()) }
        single { enginesPool ?: buildDefaultEnginePool(get()) }

        // all services, DAOs, clients and batches annotated with @KoinSingleton
        registerInjectables(eagerAllowed, "io.elephantchess.servicelayer", "io.elephantchess.db")

        includes(
            mailTemplateRenderModule(),
            batchSchedulesModule(),
        )
    }

// emails: custom wiring that can't be auto-resolved from the constructor alone
private fun mailTemplateRenderModule() =
    module {
        single {
            val config = get<AppConfig>()
            MailTemplateRender(
                baseTagResolvers = listOf(
                    UpdateMailSettingsTagResolver(config),
                    ContactLinkTagResolver(config),
                    MailFragmentResolver("email_css"),
                ),
            )
        }
    }

private fun buildDefaultEnginePool(appConfig: AppConfig): EnginePool {
    val osName = System.getProperty("os.name")
    kLogger.info { "OS name: $osName" }
    val isMacOs = osName.startsWith("Mac OS")
    if (isMacOs) kLogger.warn { "engines are disabled on Mac OS" }
    val processLocator = if (appConfig.isDockerized) DockerizedProcessLocator else LocalProcessLocator
    val configMap = if (!isMacOs && appConfig.isEnginePoolEnabled) {
        mapOf(
            PikafishEngineId to EngineConfig(
                version = appConfig.pikafishVersion,
                poolSize = 1,
                numberOfThreads = appConfig.enginesThreads,
            ),
            FairyStockfishEngineId to EngineConfig(
                version = appConfig.fairyStockfishVersion,
                poolSize = 1,
                numberOfThreads = appConfig.enginesThreads,
            ),
        )
    } else {
        mapOf()
    }

    return EnginePool(
        configMap = configMap,
        executor = Executors.newVirtualThreadPerTaskExecutor(),
        engineProcessLocator = processLocator,
    )
}
