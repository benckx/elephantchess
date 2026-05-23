/*
 * Copyright (C) 2026  Encelade SRL
 * Copyright (C) 2026  elephantchess.io
 * Copyright (C) 2026  Benoît Vleminckx (benckx)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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

object GenerateManchuGames {

    private const val TOTAL_GAMES = 10_000
    private const val GAMES_TO_KEEP = 500
    private const val MIN_PER_RESULT_TYPE = 20
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

    private fun generateGame(): GeneratedGame {
        val board = Board(Board.MANCHU_START_FEN)
        val moves = mutableListOf<HalfMove>()

        while (moves.size < MAX_MOVES_PER_GAME) {
            val colorToPlay = board.colorToPlay()

            if (board.isCheckmated()) {
                // colorToPlay is the one that is checkmated, so opponent wins
                val result = if (colorToPlay == Color.RED) GameResult.BLACK_WINS_CHECKMATE else GameResult.RED_WINS_CHECKMATE
                return GeneratedGame(randomId(), moves.map { it.toUci() }, result)
            }

            if (board.isStalemated()) {
                // colorToPlay is the one that is stalemated, so opponent wins (Xiangqi rules)
                val result = if (colorToPlay == Color.RED) GameResult.BLACK_WINS_STALEMATE else GameResult.RED_WINS_STALEMATE
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

        val allGames = (1..TOTAL_GAMES)
            .map { async(Dispatchers.Default) { generateGame() } }
            .awaitAll()

        val byResult = allGames.groupBy { it.result }.mapValues { (_, games) -> games.sortedBy { it.moves.size } }
        println("Generated: ${byResult.mapValues { it.value.size }}")

        val completedGames = allGames.filter { it.result != GameResult.TOO_LONG }

        // Pick a minimum from each terminal result type for variety
        val selected = mutableSetOf<GeneratedGame>()
        GameResult.entries.filter { it != GameResult.TOO_LONG }.forEach { resultType ->
            selected.addAll((byResult[resultType] ?: emptyList()).take(MIN_PER_RESULT_TYPE))
        }

        // Fill remaining slots with the shortest completed games
        completedGames.sortedBy { it.moves.size }.forEach { game ->
            if (selected.size < GAMES_TO_KEEP) selected.add(game)
        }

        val finalGames = selected.sortedBy { it.moves.size }.take(GAMES_TO_KEEP)
        println("Keeping ${finalGames.size} games: ${finalGames.groupBy { it.result }.mapValues { it.value.size }}")

        val outputPath = "xiangqi-core-test-utils/src/main/resources/manchu.txt"
        File(outputPath).writeText(
            finalGames.joinToString("\n") { game -> "${game.id};${game.moves.joinToString(",")}" }
        )
        println("Written to $outputPath")
    }

}
