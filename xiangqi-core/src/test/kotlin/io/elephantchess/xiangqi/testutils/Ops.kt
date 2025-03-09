package io.elephantchess.xiangqi.testutils

import io.elephantchess.xiangqi.HalfMove

object Ops {

    fun GameMovesDto.moves(): List<HalfMove> {
        return uciMoves.map { HalfMove.parseMoveFromUci(it) }
    }

}
