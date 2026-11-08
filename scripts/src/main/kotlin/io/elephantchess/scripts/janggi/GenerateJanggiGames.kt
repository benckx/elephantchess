package io.elephantchess.scripts.janggi

import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.FairyStockfishEngineId
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.xiangqi.JanggiSetup
import io.elephantchess.xiangqi.Variant
import io.elephantchess.xiangqi.generateJanggiStartFen
import io.elephantchess.xiangqi.testutils.JanggiGameMovesDtoCache
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import org.koin.core.context.stopKoin
import java.io.File
import java.util.*
import kotlin.random.Random

/**
 * Run :scripts:generateJanggiGames with optional --games, --max-moves and --max-depth arguments.
 * Uses the configured EnginePool from KoinScriptInit (scripts.properties).
 * Output columns: id;moves;result;blueSetup;redSetup. Setups use JanggiSetup enum names.
 */
object GenerateJanggiGames : KoinScriptInit() {

    private const val OUTPUT_PATH = "xiangqi-core-test-utils/src/main/resources/janggi.txt"
    private const val TOTAL_GAMES = 200
    private const val CHECKMATE_TARGET = 25
    private const val MAX_GENERATION_ATTEMPTS = 25_000
    private const val MIN_MOVES_PER_GAME = 8
    private const val MAX_MOVES_PER_GAME = 80
    private const val MIN_DEPTH = 1
    private const val MAX_DEPTH = 10

    private val random = Random(System.nanoTime())
    private val engines by inject<EnginePool>()

    internal enum class GameResult {
        CHECKMATE,
        DRAW,
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val totalGames = args.firstOrNull { it.startsWith("--games=") }?.substringAfter("=")?.toIntOrNull() ?: TOTAL_GAMES
        val maxMoves = args.firstOrNull { it.startsWith("--max-moves=") }?.substringAfter("=")?.toIntOrNull() ?: MAX_MOVES_PER_GAME
        val maxDepth = args.firstOrNull { it.startsWith("--max-depth=") }?.substringAfter("=")?.toIntOrNull() ?: MAX_DEPTH
        require(maxDepth >= MIN_DEPTH) { "max-depth must be at least $MIN_DEPTH" }
        println("totalGames=$totalGames, maxMoves=$maxMoves, maxDepth=$maxDepth")

        val games = try {
            val pool = engines
            try {
                runBlocking { generateSelfPlayGames(totalGames, maxMoves, maxDepth) }
            } finally {
                pool.close()
            }
        } finally {
            stopKoin()
        }

        val outputFile = File(OUTPUT_PATH)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(
            games.joinToString("\n") { game ->
                "${game.id};${game.moves.joinToString(",")};${game.result.name};${game.blueSetup.name};${game.redSetup.name}"
            },
        )
        println("Wrote ${games.size} unique Janggi games to $OUTPUT_PATH")
    }

    private suspend fun generateSelfPlayGames(totalGames: Int, maxMoves: Int, maxDepth: Int): List<GeneratedGame> {
        // Keep games referenced by ID in tests and CreateJanggiDrawGame.
        val retained = listOf("7f0d05cd", "cb4951a3").map { id ->
            val game = JanggiGameMovesDtoCache().findByGameId(id)
            GeneratedGame(id, game.uciMoves, GameResult.valueOf(requireNotNull(game.outcome)), game.blueSetup, game.redSetup)
        }
        val uniqueGames = retained.mapTo(linkedSetOf()) { it.key() }
        val checkmates = retained.filter { it.result == GameResult.CHECKMATE }.toMutableList()
        val otherGames = retained.filter { it.result != GameResult.CHECKMATE }.toMutableList()
        var attempts = 0

        while (
            (checkmates.size < CHECKMATE_TARGET || checkmates.size + otherGames.size < totalGames) &&
            attempts < MAX_GENERATION_ATTEMPTS
        ) {
            attempts += 1
            val game = generateSelfPlayGame(maxMoves, maxDepth)
            println("Attempt $attempts: ${game.moves.size} moves, ${game.result}; ${checkmates.size} checkmates collected")
            val key = game.key()
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

    private suspend fun generateSelfPlayGame(maxMoves: Int, maxDepth: Int): GeneratedGame {
        val blueSetup = JanggiSetup.entries.random(random)
        val redSetup = JanggiSetup.entries.random(random)
        val startFen = generateJanggiStartFen(blueSetup, redSetup)
        val history = mutableListOf<String>()
        var terminalResult = GameResult.DRAW

        suspend fun evaluate(depth: Int) =
            engines.queryForDepth(
                fen = startFen + history.joinToString(" ", prefix = " moves "),
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

        return GeneratedGame(randomId(), history, terminalResult, blueSetup, redSetup)
    }

    private fun randomId(): String =
        UUID.randomUUID().toString().replace("-", "").take(8)

    internal data class GeneratedGame(
        val id: String,
        val moves: List<String>,
        val result: GameResult,
        val blueSetup: JanggiSetup,
        val redSetup: JanggiSetup,
    ) {
        fun key(): String =
            "${blueSetup.name};${redSetup.name};${moves.joinToString(",")}"
    }
}
