package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.Position.Companion.parsePositionFromUci
import kotlin.math.ceil

/**
 * Aka a "ply". Not to be confused with a full move, which would be 2 plies or 2 half moves.
 */
data class HalfMove(val from: Position, val to: Position) {

    fun toUci() = "${from.toUci()}${to.toUci()}"

    fun toAlgebraic() = "${from.toAlgebraic()}${to.toAlgebraic()}"

    override fun toString(): String = toAlgebraic()

    companion object {

        fun halfMoveIndexToFullMove(i: Int): Int {
            return ceil(((i + 1).toDouble() / 2)).toInt()
        }

        fun parseMovesFromDigitsString(digitsString: String): List<HalfMove> {
            return digitsString
                .trim()
                .chunked(4)
                .map { chunk ->
                    val x1 = chunk[0].digitToInt()
                    val y1 = chunk[1].digitToInt()
                    val x2 = chunk[2].digitToInt()
                    val y2 = chunk[3].digitToInt()
                    HalfMove(Position(x1, y1), Position(x2, y2))
                }
        }

        fun parseMoveFromUci(move: String): HalfMove {
            val from = parsePositionFromUci(move.substring(0, 2))
            val to = parsePositionFromUci(move.substring(2, 4))
            return HalfMove(from, to)
        }

    }

}
