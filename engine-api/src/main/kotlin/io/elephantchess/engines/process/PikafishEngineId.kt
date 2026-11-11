package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator
import java.time.LocalDate
import java.time.format.DateTimeParseException

object PikafishEngineId : EngineId() {

    override val id = "PIKAFISH"

    override val displayName: String = "Pikafish"

    override fun pathOfExecutable(version: String?): String =
        pathOfExecutable(version, null, null)

    override fun pathOfExecutable(version: String?, osName: String?, osArch: String?): String {
        val requiredVersion = requireNotNull(version) {
            "Pikafish engine version must be provided in yyyy-MM-dd format to resolve the executable path."
        }
        try {
            LocalDate.parse(requiredVersion)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Pikafish engine version must use yyyy-MM-dd format.")
        }
        val effectiveOsName = osName ?: System.getProperty("os.name")
        val effectiveOsArch = osArch ?: System.getProperty("os.arch")

        val executableName = when (requiredVersion) {
            "2026-01-02" -> {
                if (effectiveOsName.startsWith("Mac OS") && effectiveOsArch in setOf("aarch64", "arm64")) {
                    "pikafish-apple-silicon"
                } else {
                    "pikafish-sse41-popcnt"
                }
            }

            "2026-09-06" -> {
                if (effectiveOsName.startsWith("Mac OS")) {
                    "Pikafish-MacOS-universal"
                } else {
                    "Pikafish-Linux-x86-64-universal"
                }
            }

            // in 2023-03-05
            else -> "pikafish-modern"
        }

        return "pikafish/$requiredVersion/$executableName"
    }

    override fun makeProcess(config: EngineConfig, engineProcessLocator: EngineProcessLocator): EngineProcess =
        PikafishEngineProcess(
            locator = engineProcessLocator,
            version = config.version,
            numberOfThreads = config.numberOfThreads,
            osName = config.osName,
            osArch = config.osArch,
        )

    // 2026-09-06 has a MacOs-universal executable,
    // while 2026-01-02 has a apple-silicon executable
    fun supportsMacOs(version: String, osArch: String): Boolean =
        version == "2026-09-06" || (version == "2026-01-02" && osArch in setOf("aarch64", "arm64"))
}
