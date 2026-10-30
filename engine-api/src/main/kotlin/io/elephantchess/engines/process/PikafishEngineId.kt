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
        val releaseDate = try {
            LocalDate.parse(requiredVersion)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Pikafish engine version must use yyyy-MM-dd format.")
        }
        val effectiveOsName = osName ?: System.getProperty("os.name")
        val effectiveOsArch = osArch ?: System.getProperty("os.arch")
        val executableName = when {
            releaseDate >= universalReleaseDate && effectiveOsName.startsWith("Mac OS") ->
                "Pikafish-MacOS-universal"
            releaseDate >= universalReleaseDate ->
                "Pikafish-Linux-x86-64-universal"
            effectiveOsName.startsWith("Mac OS") && effectiveOsArch in setOf("aarch64", "arm64") ->
                "pikafish-apple-silicon"
            releaseDate.year >= 2026 -> "pikafish-sse41-popcnt"
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

    fun supportsMacOs(version: String?): Boolean =
        version?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it >= universalReleaseDate } ?: false

    private val universalReleaseDate = LocalDate.parse("2026-09-06")
}
