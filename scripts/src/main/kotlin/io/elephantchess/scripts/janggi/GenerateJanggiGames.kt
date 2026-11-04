package io.elephantchess.scripts.janggi

import java.io.File
import java.util.UUID
import kotlin.random.Random

object GenerateJanggiGames {

    private const val OUTPUT_PATH = "xiangqi-core-test-utils/src/main/resources/janggi.txt"
    private const val TOTAL_GAMES = 200
    private const val MIN_MOVES_PER_GAME = 8
    private const val MAX_MOVES_PER_GAME = 80
    private const val TERMINAL_OUTCOME_FIELD = "CHECKMATE"
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

        val games = generateSelfPlayGames(enginePath, totalGames, maxMoves)
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

    private fun generateSelfPlayGames(enginePath: String, totalGames: Int, maxMoves: Int): List<GeneratedGame> {
        val uniqueGames = linkedSetOf<String>()
        val games = mutableListOf<GeneratedGame>()

        while (games.size < totalGames) {
            val game = generateSelfPlayGame(enginePath, maxMoves)
            val key = game.moves.joinToString(",")
            if (game.moves.size >= MIN_MOVES_PER_GAME && uniqueGames.add(key)) {
                games += game
            }
        }

        return games
    }

    private fun generateSelfPlayGame(enginePath: String, maxMoves: Int): GeneratedGame {
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
                val line = output.readLine() ?: continue
                if (predicate(line)) return line
            }
        }

        fun readBestMove(): String? {
            while (true) {
                val line = output.readLine() ?: return null
                if (line.startsWith("bestmove ")) {
                    return line.substringAfter("bestmove ").substringBefore(' ').trim()
                }
            }
        }

        try {
            send("uci")
            readUntil { it == "uciok" }
            send("isready")
            readUntil { it == "readyok" }
            send("setoption name UCI_Variant value janggitraditional")

            val history = mutableListOf<String>()
            var terminalResult = GameResult.DRAW

            while (history.size < maxMoves) {
                val depth = random.nextInt(MIN_DEPTH, MAX_DEPTH + 1)
                val jitteredDepth = if (random.nextDouble() < 0.35) random.nextInt(MIN_DEPTH, MAX_DEPTH + 1) else depth

                send("position startpos moves ${history.joinToString(" ")}")
                send("go depth $jitteredDepth")

                val bestMove = readBestMove()
                if (bestMove.isNullOrBlank() || bestMove == "(none)" || bestMove == "none") {
                    terminalResult = when {
                        history.size % 2 == 0 -> GameResult.CHECKMATE
                        else -> GameResult.STALEMATE
                    }
                    break
                }

                history += bestMove

                if (random.nextDouble() < 0.15) {
                    val extraDepth = random.nextInt(MIN_DEPTH, MAX_DEPTH + 1)
                    send("position startpos moves ${history.joinToString(" ")}")
                    send("go depth $extraDepth")
                    readBestMove()
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
