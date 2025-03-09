package io.elephantchess.scripts

import io.elephantchess.xiangqi.testutils.GameMovesDtoCache
import io.elephantchess.xiangqi.testutils.Ops.moves
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.HalfMove.Companion.halfMoveIndexToFullMove

private val cache = GameMovesDtoCache()

fun main() {
    val game = cache.findByGameId("w3SHnCJ9")
    val board = Board(keepHistory = true)
    var boardBefore: Board? = null
    game.moves().forEachIndexed { i, move ->
        try {
            boardBefore = board.copy(keepHistory = false)
            board.registerMove(move)
            println("[${halfMoveIndexToFullMove(i)}] registered ${move.toAlgebraic()}")
        } catch (e: Exception) {
            println("ERROR: ${e.message} at move ${move.toUci()} [${halfMoveIndexToFullMove(i)}]")
            println(boardBefore?.print())
        }
    }
}
