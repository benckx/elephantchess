package io.elephantchess.xiangqi.testutils

import io.elephantchess.xiangqi.HalfMove

object Ops {

    fun GameMovesDto.moves(): List<HalfMove> = uciMoves.map { HalfMove.parseMoveFromUci(it) }
}
