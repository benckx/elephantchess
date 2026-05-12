package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator
import io.elephantchess.engines.protocol.commands.LocalProcessLocator
import io.github.oshai.kotlinlogging.KotlinLogging

class FairyStockfishEngineProcess(
    locator: EngineProcessLocator = LocalProcessLocator,
    version: String?,
    private val numberOfThreads: Int,
) :
    EngineProcess(locator, FairyStockfishEngineId, version) {

    override val logger = KotlinLogging.logger {}

    override fun initEngine() {
        inputCommand("ucci")
        inputCommand("setoption Threads $numberOfThreads")
    }

}
