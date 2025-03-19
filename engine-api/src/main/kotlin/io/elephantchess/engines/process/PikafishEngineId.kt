package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator

object PikafishEngineId : EngineId() {

    override val id = "PIKAFISH"

    override val displayName: String = "Pikafish"

    override fun pathOfExecutable(version: String?) = "pikafish/$version/pikafish-modern"

    override fun makeProcess(
        config: EngineConfig,
        engineProcessLocator: EngineProcessLocator,
    ): EngineProcess =
        PikafishEngineProcess(
            locator = engineProcessLocator,
            version = config.version,
            numberOfThreads = config.numberOfThreads
        )

}
