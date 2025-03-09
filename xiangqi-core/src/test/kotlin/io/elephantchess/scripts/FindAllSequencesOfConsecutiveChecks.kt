package io.elephantchess.scripts

import io.elephantchess.xiangqi.testutils.GameMovesDtoCache
import io.elephantchess.xiangqi.testutils.Ops.moves
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.RED

private val cache = GameMovesDtoCache()

fun main() {
    cache
        .listAll()
        .forEach { game ->
            try {
                val board = Board(keepHistory = true)
                board.registerMoves(game.moves())
                val history = board.getHistory()!!

                listOf(RED, BLACK).forEach { color ->
                    val map = history.findSequencesOfConsecutiveChecks(color)
                    if (map.isNotEmpty()) {
                        println("gameId: ${game.gameId} [$color]")
                    }
                    map.toList().sortedBy { (key, _) -> key }.forEach { (key, sequence) ->
                        val fullMovesStr = sequence.fullMoves().joinToString(", ")
                        println("[$key] ${sequence.attackers} / $fullMovesStr")
                    }
                    if (map.isNotEmpty()) {
                        println()
                    }
                }
            } catch (e: Exception) {
                println("gameId: ${game.gameId} [ERROR]: ${e.message}")
                println(e.message)
                println()
            }
        }
}
