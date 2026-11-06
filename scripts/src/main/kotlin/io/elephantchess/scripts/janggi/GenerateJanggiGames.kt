package io.elephantchess.scripts.janggi

import java.io.File
import java.util.*
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

    private enum class GameResult {
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

        val games = generateSelfPlayGames(enginePath, totalGames, maxMoves, maxDepth)
            .distinctBy { it.moves.joinToString(",") }
            .take(totalGames)

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

    private fun generateSelfPlayGames(enginePath: String, totalGames: Int, maxMoves: Int, maxDepth: Int): List<GeneratedGame> {
        val uniqueGames = linkedSetOf<String>()
        val checkmates = mutableListOf<GeneratedGame>()
        val otherGames = mutableListOf<GeneratedGame>()
        var attempts = 0

        while (
            (checkmates.size < CHECKMATE_TARGET || checkmates.size + otherGames.size < totalGames) &&
            attempts < MAX_GENERATION_ATTEMPTS
        ) {
            attempts += 1
            val game = generateSelfPlayGame(enginePath, maxMoves, maxDepth)
            println("Attempt $attempts: ${game.moves.size} moves, ${game.result}; ${checkmates.size} checkmates collected")
            val key = game.moves.joinToString(",")
            if (game.moves.size < MIN_MOVES_PER_GAME || game.moves.size >= maxMoves || !uniqueGames.add(key)) {
                continue
            }

            if (game.result == GameResult.CHECKMATE) {
                if (checkmates.none { it.moves == game.moves }) {
                    checkmates += game
                }
            } else if (otherGames.none { it.moves == game.moves }) {
                otherGames += game
            }
        }

        if (checkmates.size < CHECKMATE_TARGET) {
            println(
                "Best effort: generated ${checkmates.size} checkmating games after $attempts attempts; " +
                    "target was $CHECKMATE_TARGET for $totalGames entries",
            )
        }

        val selected = (checkmates + otherGames)
            .distinctBy { it.moves.joinToString(",") }
            .take(totalGames)

        if (selected.size != totalGames) {
            error("Expected $totalGames generated Janggi games but got ${selected.size}")
        }

        return selected
    }

    private fun generateSelfPlayGame(enginePath: String, maxMoves: Int, maxDepth: Int): GeneratedGame {
        val process = ProcessBuilder(enginePath)
            .redirectErrorStream(true)
            .start()
        val input = process.outputStream.bufferedWriter()
        val output = process.inputStream.bufferedReader()

        fun send(command: String) {
            input.write(command + "\n")
            input.flush()
        }

        fun readUntil(predicate: (String) -> Boolean): String {
            while (true) {
                val line = output.readLine() ?: error("Fairy Stockfish exited before completing its handshake")
                if (predicate(line)) return line
            }
        }

        data class MoveEvaluation(val bestMove: String?, val mateDistance: Int?)

        fun readMoveEvaluation(): MoveEvaluation {
            var bestMove: String? = null
            var mateDistance: Int? = null

            while (true) {
                val line = output.readLine() ?: error("Fairy Stockfish exited before returning bestmove")
                if (line.startsWith("info ")) {
                    val mateMatch = Regex("score mate (-?\\d+)").find(line)
                    if (mateMatch != null) {
                        mateDistance = mateMatch.groupValues[1].toInt()
                    }
                }
                if (line.startsWith("bestmove ")) {
                    bestMove = line.substringAfter("bestmove ").substringBefore(' ').trim()
                    return MoveEvaluation(bestMove, mateDistance)
                }
            }
        }

        try {
            // UCCI uses the same zero-based ranks as the app on ten-rank boards.
            send("ucci")
            readUntil { it == "ucciok" }
            send("isready")
            readUntil { it == "readyok" }
            send("setoption UCI_Variant janggitraditional")

            val history = mutableListOf<String>()
            var terminalResult = GameResult.DRAW

            while (history.size < maxMoves) {
                val depth = random.nextInt(MIN_DEPTH, maxDepth + 1)
                val jitteredDepth = if (random.nextDouble() < 0.35) random.nextInt(MIN_DEPTH, maxDepth + 1) else depth

                send("position startpos moves ${history.joinToString(" ")}")
                send("go depth $jitteredDepth")

                val evaluation = readMoveEvaluation()
                val chosenMove = evaluation.bestMove

                if (chosenMove.isNullOrBlank() || chosenMove == "(none)" || chosenMove == "none") {
                    terminalResult = when {
                        evaluation.mateDistance != null -> GameResult.CHECKMATE
                        else -> GameResult.DRAW
                    }
                    break
                }

                history += chosenMove

                if (random.nextDouble() < 0.15) {
                    val extraDepth = random.nextInt(MIN_DEPTH, maxDepth + 1)
                    send("position startpos moves ${history.joinToString(" ")}")
                    send("go depth $extraDepth")
                    readMoveEvaluation()
                }
            }

            return GeneratedGame(randomId(), history, terminalResult)
        } finally {
            try {
                send("quit")
                process.waitFor()
            } finally {
                input.close()
                output.close()
            }
        }
    }

    private fun randomId(): String =
        UUID.randomUUID().toString().replace("-", "").take(8)

    private data class GeneratedGame(
        val id: String,
        val moves: List<String>,
        val result: GameResult,
    )
}
