package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator
import io.elephantchess.engines.protocol.commands.LocalProcessLocator
import io.elephantchess.xiangqi.Variant
import io.github.oshai.kotlinlogging.KotlinLogging

class FairyStockfishEngineProcess(
    locator: EngineProcessLocator = LocalProcessLocator,
    version: String?,
    private val numberOfThreads: Int,
) :
    EngineProcess(locator, FairyStockfishEngineId, version) {

    override val logger = KotlinLogging.logger {}

    private var currentVariant: Variant? = null

    override fun initEngine() {
        inputCommand("ucci")
        inputCommand("setoption Threads $numberOfThreads")
    }

    override fun setVariant(variant: Variant) {
        if (variant != currentVariant) {
            inputCommand("setoption name UCI_Variant value ${variant.fairyStockfishVariantName()}")
            currentVariant = variant
        }
    }

}
