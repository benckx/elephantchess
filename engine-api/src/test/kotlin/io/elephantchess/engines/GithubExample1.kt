package io.elephantchess.engines

import io.elephantchess.engines.process.EngineConfig
import io.elephantchess.engines.process.PikafishEngineId
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors.newFixedThreadPool

fun main() {
    val engineConfig = EngineConfig("2022-12-26", poolSize = 1, numberOfThreads = 8)
    val enginePool = EnginePool(mapOf(PikafishEngineId to engineConfig), newFixedThreadPool(2))

    runBlocking {
        val fen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0"
        val infoLinesResult = enginePool.queryForDepth(fen, PikafishEngineId, 10)
        val infoLineResult = infoLinesResult?.deepestResult()
        println("engine result: ${infoLineResult?.line}")
        println("best move: ${infoLineResult?.pv?.first()}")
    }

    enginePool.close()
}
