package io.elephantchess.xiangqi.testutils

import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.XiangqiBoard

object Ops {

    fun GameMovesDto.endsInCheckmate(): Boolean {
        val board = XiangqiBoard()
        board.registerMoves(uciMoves.map { parseMoveFromUci(it) })
        return board.isCheckmated()
    }
}
