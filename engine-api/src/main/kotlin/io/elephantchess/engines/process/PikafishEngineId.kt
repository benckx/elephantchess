package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator
import java.time.LocalDate
import java.time.format.DateTimeParseException

object PikafishEngineId : EngineId() {

    override val id = "PIKAFISH"

    override val displayName: String = "Pikafish"

    override fun pathOfExecutable(version: String?): String {
        val requiredVersion = requireNotNull(version) {
            "Pikafish engine version must be provided in yyyy-MM-dd format to resolve the executable path."
        }
        val releaseDate = try {
            LocalDate.parse(requiredVersion)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Pikafish engine version must use yyyy-MM-dd format.")
        }
        val executableName = if (releaseDate.year >= 2026) {
            "pikafish-sse41-popcnt"
        } else {
            "pikafish-modern"
        }

        return "pikafish/$requiredVersion/$executableName"
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
