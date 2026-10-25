package io.elephantchess.servicelayer.koin

import io.elephantchess.config.AppConfig
import io.elephantchess.config.ArgConfig
import io.elephantchess.config.DbConfig
import io.elephantchess.config.loadAppConfig
import io.elephantchess.db.utils.getDslContext
import io.elephantchess.engines.EnginePool
import io.elephantchess.servicelayer.services.MailTemplateRender
import io.elephantchess.servicelayer.services.resolvers.ContactLinkTagResolver
import io.elephantchess.servicelayer.services.resolvers.MailFragmentResolver
import io.elephantchess.servicelayer.services.resolvers.UpdateMailSettingsTagResolver
import io.elephantchess.servicelayer.utils.ops.registerInjectables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jooq.DSLContext
import org.koin.dsl.module

fun serviceLayerModule(
    argConfig: ArgConfig,
    eagerAllowed: Boolean = false,
    dslBuilder: (DbConfig) -> DSLContext = { getDslContext(it) },
    enginesPool: EnginePool? = null,
) =
    module {
        // config
        val appConfig = loadAppConfig(argConfig)
        single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single { appConfig }

        // db access
        single { appConfig.dbConfig }
        single { dslBuilder(get()) }

        // engines
        single { enginesPool ?: buildDefaultEnginePool(appConfig) }

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
