package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator
import io.elephantchess.engines.protocol.commands.LocalProcessLocator
import io.elephantchess.xiangqi.Variant
import io.github.oshai.kotlinlogging.KotlinLogging

class FairyStockfishEngineProcess(
    locator: EngineProcessLocator = LocalProcessLocator,
    version: String?,
    private val numberOfThreads: Int,
    osName: String? = null,
    osArch: String? = null,
) : EngineProcess(locator, FairyStockfishEngineId, version, osName, osArch) {

    override val logger = KotlinLogging.logger {}

    private var currentVariant: Variant? = null

    override fun initEngine() {
        inputCommand("ucci")
        inputCommand("setoption Threads $numberOfThreads")
    }

    override fun setVariant(variant: Variant) {
        if (variant != currentVariant) {
            inputCommand("setoption UCI_Variant ${variant.fairyStockfishVariantName()}")
            currentVariant = variant
        }
    }
}

/**
 * The Fairy Stockfish variant name used with `setoption name UCI_Variant value <name>`.
 */
fun Variant.fairyStockfishVariantName(): String =
    when (this) {
        Variant.XIANGQI -> "xiangqi"
        Variant.MANCHU -> "manchu"
        Variant.JANGGI -> "janggitraditional"
    }
