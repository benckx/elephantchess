package io.elephantchess.scripts

import io.elephantchess.config.ArgConfig
import io.elephantchess.engines.EnginePool
import io.elephantchess.scripts.utils.ScriptConfig.Companion.loadScriptConfig
import io.elephantchess.scripts.utils.getScriptDslContext
import io.elephantchess.servicelayer.serviceLayerModule
import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin

interface KoinScript : KoinComponent {

    fun initKoin(
        enginesPool: EnginePool? = null,
        appProfile: String? = null,
        configurationLocation: String? = null
    ) {
        val scriptConfig = loadScriptConfig()
        val profile = appProfile ?: scriptConfig.appProfile ?: "local"

        startKoin {
            modules(
                serviceLayerModule(
                    argConfig = ArgConfig(
                        profile = profile,
                        configLocation = configurationLocation
                            ?: if (profile != "local") scriptConfig.configurationLocation else null
                    ),
                    dslBuilder = { getScriptDslContext(it) },
                    enginesPool = enginesPool
                )
            )
        }
    }

}
