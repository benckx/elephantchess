package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator

object FairyStockfishEngineId : EngineId() {

    override val id: String = "FAIRYSTOCKFISH"
    override val displayName = "Fairy Stockfish"
    override val supportsNonStandardFens = true

    override fun pathOfExecutable(version: String?) = "fairy-stockfish"

    override fun makeProcess(
        config: EngineConfig,
        engineProcessLocator: EngineProcessLocator,
    ): EngineProcess =
        FairyStockfishEngineProcess(
            locator = engineProcessLocator,
            numberOfThreads = config.numberOfThreads
        )

}
