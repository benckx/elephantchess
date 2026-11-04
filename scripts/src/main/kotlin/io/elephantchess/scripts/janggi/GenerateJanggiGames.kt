package io.elephantchess.scripts.janggi

import java.io.File
import java.util.UUID
import kotlin.random.Random

object GenerateJanggiGames {

    private const val OUTPUT_PATH = "xiangqi-core-test-utils/src/main/resources/janggi.txt"
    private const val TOTAL_GAMES = 250
    private const val MAX_MOVES_PER_GAME = 120
    private const val MIN_DEPTH = 1
    private const val MAX_DEPTH = 10

    private val random = Random(System.nanoTime())

    @JvmStatic
    fun main(args: Array<String>) {
        val totalGames = args.firstOrNull { it.startsWith("--games=") }?.substringAfter("=")?.toIntOrNull() ?: TOTAL_GAMES
        val maxMoves = args.firstOrNull { it.startsWith("--max-moves=") }?.substringAfter("=")?.toIntOrNull() ?: MAX_MOVES_PER_GAME
        val enginePath = args.firstOrNull { it.startsWith("--engine=") }?.substringAfter("=")
            ?: System.getenv("FAIRY_STOCKFISH_PATH")
            ?: findDefaultEnginePath()

        val games = if (enginePath != null) {
            generateSelfPlayGames(enginePath, totalGames, maxMoves)
        } else {
            generateSyntheticGames(totalGames, maxMoves)
        }

        val outputFile = File(OUTPUT_PATH)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(
            games.joinToString("\n") { game -> "${game.id};${game.moves.joinToString(",")}" },
        )
        println("Wrote ${games.size} Janggi games to $OUTPUT_PATH")
    }

    private fun findDefaultEnginePath(): String? {
        val candidates = listOf(
            "fairy/fairy-stockfish",
            "fairy/14.0.1/fairy-stockfish",
            "fairy-stockfish",
            "/usr/local/bin/fairy-stockfish",
            "/opt/fairy-stockfish/fairy-stockfish",
        )
        return candidates.firstOrNull { File(it).canExecute() }
    }

    private fun generateSelfPlayGames(enginePath: String, totalGames: Int, maxMoves: Int): List<GeneratedGame> =
        (1..totalGames).map { generateSelfPlayGame(enginePath, maxMoves) }

    private fun generateSyntheticGames(totalGames: Int, maxMoves: Int): List<GeneratedGame> =
        (1..totalGames).map {
            val moves = buildList {
                repeat(random.nextInt(8, maxMoves.coerceAtLeast(8) / 2)) {
                    add(listOf("d0a2", "d2c2", "b0b2", "d9d8", "a0a1", "h0h2", "a8a7", "g9g8").random())
                }
            }
            GeneratedGame(randomId(), moves)
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

        send("uci")
        readUntil { it == "uciok" }
        send("isready")
        readUntil { it == "readyok" }
        send("setoption name UCI_Variant value janggitraditional")

        val history = mutableListOf<String>()
        while (history.size < maxMoves) {
            val depth = random.nextInt(MIN_DEPTH, MAX_DEPTH + 1)
            val jitteredDepth = if (random.nextDouble() < 0.35) random.nextInt(MIN_DEPTH, MAX_DEPTH + 1) else depth
            send("position startpos moves ${history.joinToString(" ")}")
            send("go depth $jitteredDepth")
            var bestMove: String? = null
            while (true) {
                val line = output.readLine() ?: break
                if (line.startsWith("bestmove ")) {
                    bestMove = line.substringAfter("bestmove ").substringBefore(' ').trim()
                    break
                }
            }
            if (bestMove.isNullOrBlank() || bestMove == "(none)" || bestMove == "none") {
                break
            }
            history += bestMove
            if (random.nextDouble() < 0.15) {
                val extraDepth = random.nextInt(MIN_DEPTH, MAX_DEPTH + 1)
                send("position startpos moves ${history.joinToString(" ")}")
                send("go depth $extraDepth")
                while (true) {
                    val line = output.readLine() ?: break
                    if (line.startsWith("bestmove ")) break
                }
            }
        }

        send("quit")
        process.waitFor()
        input.close()
        output.close()
        return GeneratedGame(randomId(), history)
    }

    private fun randomId(): String =
        UUID.randomUUID().toString().replace("-", "").take(8)

    private data class GeneratedGame(val id: String, val moves: List<String>)
}
