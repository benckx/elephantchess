package io.elephantchess.servicelayer.koin

import io.elephantchess.config.AppConfig
import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.EngineConfig
import io.elephantchess.engines.process.FairyStockfishEngineId
import io.elephantchess.engines.process.PikafishEngineId
import io.elephantchess.engines.protocol.commands.LocalProcessLocator
import io.elephantchess.servicelayer.utils.DockerizedProcessLocator
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.Executors

internal fun buildDefaultEnginePool(appConfig: AppConfig): EnginePool {
    val logger = KotlinLogging.logger("io.elephantchess.servicelayer.koin.buildDefaultEnginePool")
    val osName = System.getProperty("os.name")
    logger.info { "OS name: $osName" }
    val isMacOs = osName.startsWith("Mac OS")
    if (isMacOs) logger.warn { "engines are disabled on Mac OS" }
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

    val processLocator = if (appConfig.isDockerized) DockerizedProcessLocator else LocalProcessLocator
    return EnginePool(
        configMap = configMap,
        executor = Executors.newVirtualThreadPerTaskExecutor(),
        engineProcessLocator = processLocator,
    )
}
