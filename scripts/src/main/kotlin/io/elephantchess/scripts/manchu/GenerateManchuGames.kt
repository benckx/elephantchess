package io.elephantchess.scripts.manchu

import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.HalfMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object GenerateManchuGames {

    private const val TOTAL_GAMES = 20_000
    private const val GAMES_TO_KEEP = 1_000
    private const val MIN_PER_RESULT_TYPE = 100
    private const val MAX_MOVES_PER_GAME = 400

    private enum class GameResult {
        RED_WINS_CHECKMATE,
        BLACK_WINS_CHECKMATE,
        RED_WINS_STALEMATE,
        BLACK_WINS_STALEMATE,
        TOO_LONG,
    }

    private data class GeneratedGame(
        val id: String,
        val moves: List<String>,
        val result: GameResult,
    )

    private fun movesKey(game: GeneratedGame): String = game.moves.joinToString(",")

    private fun generateGame(): GeneratedGame {
        val board = Board(Board.MANCHU_START_FEN)
        val moves = mutableListOf<HalfMove>()

        while (moves.size < MAX_MOVES_PER_GAME) {
            val colorToPlay = board.colorToPlay()

            if (board.isCheckmated()) {
                // colorToPlay is the one that is checkmated, so opponent wins
                val result =
                    if (colorToPlay == Color.RED) GameResult.BLACK_WINS_CHECKMATE else GameResult.RED_WINS_CHECKMATE
                return GeneratedGame(randomId(), moves.map { it.toUci() }, result)
            }

            if (board.isStalemated()) {
                // colorToPlay is the one that is stalemated, so opponent wins (Xiangqi rules)
                val result =
                    if (colorToPlay == Color.RED) GameResult.BLACK_WINS_STALEMATE else GameResult.RED_WINS_STALEMATE
                return GeneratedGame(randomId(), moves.map { it.toUci() }, result)
            }

            val legalMoves = board.listLegalMoves(colorToPlay)
            if (legalMoves.isEmpty()) break

            val move = legalMoves.random()
            board.registerMove(move)
            moves.add(move)
        }

        return GeneratedGame(randomId(), moves.map { it.toUci() }, GameResult.TOO_LONG)
    }

    private fun randomId(): String = UUID.randomUUID().toString().replace("-", "").take(8)

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        println("Generating $TOTAL_GAMES Manchu games in parallel...")

        val startedAt = System.currentTimeMillis()
        val completed = AtomicInteger(0)

        val allGames = (1..TOTAL_GAMES)
            .map {
                async(Dispatchers.Default) {
                    val game = generateGame()
                    val done = completed.incrementAndGet()
                    if (done % 500 == 0 || done == TOTAL_GAMES) {
                        val elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000
                        println("Progress: $done/$TOTAL_GAMES (${done * 100 / TOTAL_GAMES}%) - ${elapsedSeconds}s elapsed")
                    }
                    game
                }
            }
            .awaitAll()

        val uniqueGames = allGames.distinctBy { movesKey(it) }
        println("Deduplicated ${allGames.size - uniqueGames.size} duplicate games")

        val byResult = uniqueGames.groupBy { it.result }.mapValues { (_, games) -> games.sortedBy { it.moves.size } }
        println("Generated: ${byResult.mapValues { it.value.size }}")

        val completedGames = uniqueGames.filter { it.result != GameResult.TOO_LONG }

        // Pick a minimum from each terminal result type for variety
        val selectedByMoves = linkedMapOf<String, GeneratedGame>()
        GameResult.entries.filter { it != GameResult.TOO_LONG }.forEach { resultType ->
            (byResult[resultType] ?: emptyList())
                .take(MIN_PER_RESULT_TYPE)
                .forEach { game -> selectedByMoves.putIfAbsent(movesKey(game), game) }
        }

        // Fill remaining slots with the shortest completed games
        completedGames.sortedBy { it.moves.size }.forEach { game ->
            if (selectedByMoves.size < GAMES_TO_KEEP) {
                selectedByMoves.putIfAbsent(movesKey(game), game)
            }
        }

        val finalGames = selectedByMoves.values.sortedBy { it.moves.size }.take(GAMES_TO_KEEP)
        println("Keeping ${finalGames.size} games: ${finalGames.groupBy { it.result }.mapValues { it.value.size }}")

        val outputPath = "xiangqi-core-test-utils/src/main/resources/manchu.txt"
        File(outputPath).writeText(
            finalGames.joinToString("\n") { game -> "${game.id};${game.moves.joinToString(",")}" }
        )
        println("Written to $outputPath")
    }

}
