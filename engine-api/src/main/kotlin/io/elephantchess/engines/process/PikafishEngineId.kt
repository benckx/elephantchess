package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator

object PikafishEngineId : EngineId() {

    override val id = "PIKAFISH"

    override val displayName: String = "Pikafish"

    override fun pathOfExecutable(version: String?): String {
        val releaseYear = version?.substringBefore("-")?.toIntOrNull()
        val executableName = if (releaseYear != null && releaseYear >= 2026) {
            "pikafish-sse41-popcnt"
        } else {
            "pikafish-modern"
        }

        return "pikafish/$version/$executableName"
    }

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
