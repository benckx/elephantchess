package io.elephantchess.xiangqi.testutils

import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci

object Ops {

    fun GameMovesDto.endsInCheckmate(): Boolean {
        val board = Board()
        board.registerMoves(uciMoves.map { parseMoveFromUci(it) })
        return board.isCheckmated()
    }

}
