package io.elephantchess.scripts.analysis

import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.EngineConfig
import io.elephantchess.engines.process.EngineId
import io.elephantchess.engines.process.PikafishEngineId
import io.elephantchess.xiangqi.HalfMove
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

private val engineConfigs: Map<EngineId, EngineConfig> = mapOf(PikafishEngineId to EngineConfig("2022-12-26", 1, 8))
private val enginePool = EnginePool(engineConfigs, Executors.newVirtualThreadPerTaskExecutor())

fun main() {
    val fen1 = "r1bakabn1/6r2/1cn3c2/p1p3p1p/4C4/1C7/P1P1P1P1P/6N2/9/RNBAKABR1 b - - 0 0"
    val fen2 = "1rbakabn1/6r2/1cn3c2/p1p3p1p/4C4/1C7/P1P1P1P1P/6N2/9/RNBAKABR1 w - - 0 0"

    runBlocking {
        val result1 = enginePool.queryForDepth(fen1, PikafishEngineId, 16, 180_000L)
        val result2 = enginePool.queryForDepth(fen2, PikafishEngineId, 16, 180_000L)

        result1?.deepestResult()?.let { println("[$fen1] PV ${toAlgebraic(it.pv)}") }
        result2?.deepestResult()?.let { println("[$fen2] PV ${toAlgebraic(it.pv)}") }
    }

    enginePool.close()
}

private fun toAlgebraic(pv: List<String>): List<String> {
    return pv.map { HalfMove.parseMoveFromUci(it).toAlgebraic() }
}
