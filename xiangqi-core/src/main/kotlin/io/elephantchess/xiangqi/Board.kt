package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.Variant.MANCHU
import io.elephantchess.xiangqi.Variant.XIANGQI

/**
 * Compatibility entry point for existing Xiangqi and Manchu callers.
 * New callers can use [XiangqiBoard] or [JanggiBoard] explicitly.
 */
class Board(
    initFen: String = DEFAULT_START_FEN,
    keepHistory: Boolean = false,
    variant: Variant = inferVariantFromFen(initFen),
) : XiangqiBoard(initFen, keepHistory, variant) {

    override fun copy(keepHistory: Boolean): Board =
        copyInto(Board(keepHistory = keepHistory, variant = variant), keepHistory)

    companion object {

        const val WIDTH = 9
        const val HEIGHT = 10
        const val DEFAULT_START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0"

        /**
         * Manchu chess start FEN. Black pieces are set up as in standard xiangqi.
         * Red has only: general, 2 advisors, 2 elephants, 5 soldiers, and the super-chariot (M) at a1.
         * The super-chariot ('M'/'m') combines the powers of the chariot, horse, and cannon.
         */
        const val MANCHU_START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/9/9/M1BAKAB2 w - - 0 0"
        const val PRINT_SEPARATOR = " "
        const val UCI_MOVE_REGEX_PATTERN = "[a-i][0-9][a-i][0-9]"

        fun defaultStartFen(variant: Variant): String =
            when (variant) {
                XIANGQI -> DEFAULT_START_FEN
                MANCHU -> MANCHU_START_FEN
                Variant.JANGGI -> JanggiBoard.DEFAULT_START_FEN
            }

        fun inferVariantFromFen(fen: String): Variant =
            if (fen.substringBefore(' ').contains('M')) MANCHU else XIANGQI

        fun validateFen(fen: String) {
            try {
                Board(fen)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid FEN: $fen: ${e.message}")
            }
        }

        fun colorToPlay(fen: String): Color =
            Board(fen).colorToPlay()

        fun isMoveLegal(currentFen: String, move: String): Boolean =
            try {
                Board(currentFen).isLegalMove(parseMoveFromUci(move))
            } catch (e: Exception) {
                false
            }

        fun calculateNewFen(oldFen: String, move: String): String {
            val board = Board(oldFen)
            board.registerMove(move)
            return board.outputFen()
        }

        fun isCheckmated(fen: String): Boolean =
            Board(fen).isCheckmated()

        fun isStalemated(fen: String): Boolean =
            Board(fen).isStalemated()

        fun isInCheck(fen: String, color: Color): Boolean =
            Board(fen).isInCheck(color)

        fun moveToFens(movesHistory: List<String>, startFen: String = DEFAULT_START_FEN, abridged: Boolean = false): List<String> {
            val board = Board(startFen)
            val fens = mutableListOf(board.outputFen(abridged))
            movesHistory.forEach { move ->
                board.registerMove(move)
                fens += board.outputFen(abridged)
            }
            return fens.toList()
        }

        fun resetFullMoveCount(fen: String): String =
            fen.split(" ").dropLast(1).joinToString(" ") + " 0"
    }
}
