package io.elephantchess.engines

import io.elephantchess.engines.process.EngineConfig
import io.elephantchess.engines.process.EngineId
import io.elephantchess.engines.process.PikafishEngineId
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import kotlinx.coroutines.*
import java.util.Collections.synchronizedList
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

/**
 * Not really a unit test because it would be slow to run during the build,
 * and also because it would require engine binaries to be accessible
 */
fun main() {
    val n = 100
    val fens = listRandomFens(n)
    val results = mutableListOf<ExperimentResult>()

    val allPoolSizes = listOf(128, 64, 32, 16, 8, 4, 2, 1)
    val allDepths = listOf(20, 16, 14, 12, 10, 8, 6)

    allPoolSizes.forEach { poolSize ->
        allDepths.forEach { depth ->
            runBlocking {
                results += runExperiment(fens, poolSize = poolSize, depth = depth)
            }
        }
    }

    println()

    results
        .sortedBy { result -> result.depth }
        .sortedBy { result -> result.poolSize }
        .sortedBy { result -> result.isCorrect() }
        .forEach { result -> println(result) }

    val totalBatchRequests = results.sumOf { it.batchSize }
    val totalMissingResults = results.sumOf { it.missingResults() }
    val totalIllegalResults = results.sumOf { it.illegalResults() }
    val totalAvailableResults = totalBatchRequests - totalMissingResults
    val missingResultsRate = totalMissingResults.toDouble() / totalBatchRequests
    val illegalResultsRate = totalIllegalResults.toDouble() / totalAvailableResults

    println()
    println("missing: ${String.format("%.4f", missingResultsRate * 100)}%")
    println("illegal: ${String.format("%.4f", illegalResultsRate * 100)}%")
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun runExperiment(fens: List<String>, poolSize: Int, depth: Int): ExperimentResult {
    val results = synchronizedList(mutableListOf<FenAndResult>())
    val engineConfigs: Map<EngineId, EngineConfig> = mapOf(PikafishEngineId to EngineConfig("2022-12-26", poolSize, 1))
    val enginePool = EnginePool(engineConfigs, Executors.newFixedThreadPool(poolSize))

    val timeMs = measureTimeMillis {
        val jobs = fens.map { fen ->
            GlobalScope.launch(Dispatchers.Default) {
                enginePool
                    .queryForDepth(fen, PikafishEngineId, depth, 180_000)
                    ?.let { infoLinesResult ->
                        results += FenAndResult(fen, infoLinesResult.bestMove)
                    }
            }
        }

        jobs.forEach { it.join() }
    }

    results.filter { it.bestMove == null }.forEach { result ->
        println("no best move for fen: ${result.fen}")
    }

    val totalBestMoves = results.count { it.bestMove != null }
    val legalBestMoves = results.count { it.isBestMoveLegal() }
    val isCorrect = totalBestMoves == legalBestMoves
    val logTag = "[poolSize=$poolSize, depth=$depth]"

    println("$logTag total process time: ${(timeMs / 1000).toInt()} sec.")
    println("$logTag results: ${results.size} / ${fens.size}")
    println("$logTag results with best move: $totalBestMoves / ${fens.size}")
    println("$logTag results with legal best move: $legalBestMoves / ${fens.size}")
    if (isCorrect) {
        println("$logTag CORRECT")
    } else {
        println("$logTag INCORRECT")
    }

    enginePool.close()

    return ExperimentResult(
        batchSize = fens.size,
        poolSize = poolSize,
        depth = depth,
        totalBestMoves = totalBestMoves,
        legalBestMoves = legalBestMoves,
        timeMs = timeMs
    )
}

private fun listRandomFens(n: Int): List<String> {
    fun getResourceAsText(path: String): String =
        object {}.javaClass.getResource(path)?.readText()!!

    return getResourceAsText("/data/fens.txt").split("\n").shuffled().take(n)
}

private data class ExperimentResult(
    val batchSize: Int,
    val poolSize: Int,
    val depth: Int,
    val totalBestMoves: Int,
    val legalBestMoves: Int,
    val timeMs: Long,
) {

    fun isCorrect(): Boolean {
        return totalBestMoves == legalBestMoves && totalBestMoves == batchSize
    }

    fun missingResults(): Int {
        return batchSize - totalBestMoves
    }

    fun illegalResults(): Int {
        return totalBestMoves - legalBestMoves
    }

    override fun toString(): String {
        val correctStr = if (isCorrect()) "CORRECT" else "INCORRECT"
        val timeStr = if (timeMs > 1000) "${(timeMs / 1000).toInt()} sec." else "$timeMs ms."

        return "[$correctStr] poolSize=$poolSize, " +
                "depth=$depth, " +
                "hasBestMoves=$totalBestMoves, " +
                "legalBestMoves=$legalBestMoves, " +
                "time=$timeStr"
    }

}

private data class FenAndResult(val fen: String, val bestMove: String?) {

    fun isBestMoveLegal(): Boolean {
        bestMove?.let { bestMove ->
            return Board(fen).isLegalMove(parseMoveFromUci(bestMove))
        }
        return false
    }

}
