package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator

object FairyStockfishEngineId : EngineId() {

    override val id: String = "FAIRYSTOCKFISH"
    override val displayName = "Fairy Stockfish"
    override val supportsNonStandardFens = true

    override fun pathOfExecutable(version: String?): String {
        val requiredVersion = requireNotNull(version) {
            "Fairy Stockfish engine version must be provided to resolve the executable path."
        }

        return "fairy/$requiredVersion/fairy-stockfish"
    }

    override fun makeProcess(
        config: EngineConfig,
        engineProcessLocator: EngineProcessLocator,
    ): EngineProcess =
        FairyStockfishEngineProcess(
            locator = engineProcessLocator,
            version = config.version,
            numberOfThreads = config.numberOfThreads
        )

}
