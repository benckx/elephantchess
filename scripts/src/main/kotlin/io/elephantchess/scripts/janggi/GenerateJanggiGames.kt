package io.elephantchess.scripts.janggi

import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.EngineConfig
import io.elephantchess.engines.process.FairyStockfishEngineId
import io.elephantchess.engines.protocol.commands.EngineProcessLocator
import io.elephantchess.xiangqi.JanggiBoard.Companion.DEFAULT_JANGGI_START_FEN
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*
import java.util.concurrent.Executors
import kotlin.random.Random

object GenerateJanggiGames {

    private const val OUTPUT_PATH = "xiangqi-core-test-utils/src/main/resources/janggi.txt"
    private const val TOTAL_GAMES = 100
    private const val CHECKMATE_TARGET = 25
    private const val MAX_GENERATION_ATTEMPTS = 25_000
    private const val MIN_MOVES_PER_GAME = 8
    private const val MAX_MOVES_PER_GAME = 80
    private const val MIN_DEPTH = 1
    private const val MAX_DEPTH = 10

    private val random = Random(System.nanoTime())

    internal enum class GameResult {
        CHECKMATE,
        STALEMATE,
        DRAW,
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val totalGames = args.firstOrNull { it.startsWith("--games=") }?.substringAfter("=")?.toIntOrNull() ?: TOTAL_GAMES
        val maxMoves = args.firstOrNull { it.startsWith("--max-moves=") }?.substringAfter("=")?.toIntOrNull() ?: MAX_MOVES_PER_GAME
        val maxDepth = args.firstOrNull { it.startsWith("--max-depth=") }?.substringAfter("=")?.toIntOrNull() ?: MAX_DEPTH
        require(maxDepth >= MIN_DEPTH) { "max-depth must be at least $MIN_DEPTH" }
        val enginePath = args.firstOrNull { it.startsWith("--engine=") }?.substringAfter("=")
            ?: System.getenv("FAIRY_STOCKFISH_PATH")
            ?: findDefaultEnginePath()
            ?: error(
                "Fairy Stockfish is required to generate Janggi fixtures. " +
                    "Set FAIRY_STOCKFISH_PATH or install a binary at one of: " +
                    listOf(
                        "engines/fairy/14.0.1/fairy-stockfish",
                        "engines/fairy/11.2/fairy-stockfish",
                        "fairy/fairy-stockfish",
                        "fairy/14.0.1/fairy-stockfish",
                        "fairy-stockfish",
                        "/usr/local/bin/fairy-stockfish",
                        "/opt/fairy-stockfish/fairy-stockfish",
                    ).joinToString(", "),
            )

        println("totalGames=$totalGames, maxMoves=$maxMoves, maxDepth=$maxDepth, enginePath=$enginePath")

        val executor = Executors.newSingleThreadExecutor()
        val engines = try {
            EnginePool(
                mapOf(FairyStockfishEngineId to EngineConfig(version = "14.0.1")),
                executor,
                object : EngineProcessLocator {
                    override fun launchCommand(binFileName: String) =
                        enginePath
                    override fun launchArguments(binFileName: String) =
                        listOf(enginePath)
                },
            )
        } catch (e: Exception) {
            executor.shutdownNow()
            throw e
        }
        val games = try {
            runBlocking { generateSelfPlayGames(engines, totalGames, maxMoves, maxDepth) }
        } finally {
            engines.close()
        }

        val outputFile = File(OUTPUT_PATH)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(
            games.joinToString("\n") { game -> "${game.id};${game.moves.joinToString(",")};${game.result.name}" },
        )
        println("Wrote ${games.size} unique Janggi games to $OUTPUT_PATH")
    }

    private fun findDefaultEnginePath(): String? {
        val candidates = listOf(
            "engines/fairy/14.0.1/fairy-stockfish",
            "engines/fairy/11.2/fairy-stockfish",
            "fairy/fairy-stockfish",
            "fairy/14.0.1/fairy-stockfish",
            "fairy-stockfish",
            "/usr/local/bin/fairy-stockfish",
            "/opt/fairy-stockfish/fairy-stockfish",
        )
        return candidates.firstOrNull { File(it).canExecute() }
    }

    private suspend fun generateSelfPlayGames(engines: EnginePool, totalGames: Int, maxMoves: Int, maxDepth: Int): List<GeneratedGame> {
        val uniqueGames = linkedSetOf<String>()
        val checkmates = mutableListOf<GeneratedGame>()
        val otherGames = mutableListOf<GeneratedGame>()
        var attempts = 0

        while (
            (checkmates.size < CHECKMATE_TARGET || checkmates.size + otherGames.size < totalGames) &&
            attempts < MAX_GENERATION_ATTEMPTS
        ) {
            attempts += 1
            val game = generateSelfPlayGame(engines, maxMoves, maxDepth)
            println("Attempt $attempts: ${game.moves.size} moves, ${game.result}; ${checkmates.size} checkmates collected")
            val key = game.moves.joinToString(",")
            if (game.moves.size < MIN_MOVES_PER_GAME || game.moves.size >= maxMoves || !uniqueGames.add(key)) {
                continue
            }

            if (game.result == GameResult.CHECKMATE) {
                checkmates += game
            } else {
                otherGames += game
            }
        }

        if (checkmates.size < CHECKMATE_TARGET) {
            println(
                "Best effort: generated ${checkmates.size} checkmating games after $attempts attempts; " +
                    "target was $CHECKMATE_TARGET for $totalGames entries",
            )
        }

        val selected = (checkmates + otherGames).take(totalGames)

        if (selected.size != totalGames) {
            error("Expected $totalGames generated Janggi games but got ${selected.size}")
        }

        return selected
    }

    internal suspend fun generateSelfPlayGame(engines: EnginePool, maxMoves: Int, maxDepth: Int): GeneratedGame {
        val history = mutableListOf<String>()
        var terminalResult = GameResult.DRAW

        suspend fun evaluate(depth: Int) =
            engines.queryForDepth(
                fen = DEFAULT_JANGGI_START_FEN + history.joinToString(" ", prefix = " moves "),
                engineId = FairyStockfishEngineId,
                depth = depth,
                variant = Variant.JANGGI,
                checkPeriod = 10,
                waitForBestMove = true,
            ) ?: error("Fairy Stockfish failed to evaluate the Janggi position")

        while (history.size < maxMoves) {
            val depth = random.nextInt(MIN_DEPTH, maxDepth + 1)
            val jitteredDepth = if (random.nextDouble() < 0.35) random.nextInt(MIN_DEPTH, maxDepth + 1) else depth
            val evaluation = evaluate(jitteredDepth)
            val chosenMove = evaluation.bestMove

            if (evaluation.checkmate || chosenMove == "(none)" || chosenMove == "none") {
                terminalResult = if (evaluation.infoLines.any { it.mate != null }) GameResult.CHECKMATE else GameResult.DRAW
                break
            }
            check(!chosenMove.isNullOrBlank()) { "Fairy Stockfish did not return a best move" }
            history += chosenMove

            if (random.nextDouble() < 0.15) {
                evaluate(random.nextInt(MIN_DEPTH, maxDepth + 1))
            }
        }

        return GeneratedGame(randomId(), history, terminalResult)
    }

    private fun randomId(): String =
        UUID.randomUUID().toString().replace("-", "").take(8)

    internal data class GeneratedGame(
        val id: String,
        val moves: List<String>,
        val result: GameResult,
    )
}
