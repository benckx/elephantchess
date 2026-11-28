package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.AbstractPieceType.*
import io.elephantchess.xiangqi.Color.WHITE
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.JanggiBoard.Companion.DEFAULT_JANGGI_START_FEN
import io.elephantchess.xiangqi.Variant.*

open class XiangqiBoard(
    initFen: String = DEFAULT_XIANGQI_START_FEN,
    keepHistory: Boolean = false,
    override val variant: Variant = inferVariantFromFen(initFen),
) : AbstractXiangqiBoard(initFen, keepHistory) {

    init {
        require(variant != JANGGI) { "Use JanggiBoard for Janggi rules" }
    }

    override fun isIllegal(move: HalfMove, color: Color): Boolean {
        val copy = copy()
        copy.registerMove(move)
        return copy.areGeneralsFacing() || copy.isInCheck(color)
    }

    override fun listAllMoves(pieceAtPosition: PieceAtPosition): List<HalfMove> {
        val position = pieceAtPosition.position
        val color = pieceAtPosition.color()
        require(pieceAtPosition.physicalPiece == pieceAt(position))

        fun cannonTargets(): List<Position> =
            position.getOrthogonalLines().flatMap { line ->
                val clear = line.takeWhile { !hasPieceAt(it) }
                val capture = line.drop(clear.size + 1).firstOrNull { hasPieceAt(it) }
                clear + listOfNotNull(capture?.takeIf { containsOppositeColors(position, it) })
            }

        fun inPalace(targets: List<Position>): List<Position> =
            targets.filter { if (color == WHITE) it.isInWhitePalace() else it.isInBlackPalace() }

        val targets = when (pieceAtPosition.abstractPieceType()) {
            CHARIOT -> chariotTargets(position, position.getOrthogonalLines())

            CANNON -> cannonTargets()

            HORSE -> horseTargets(position)

            SUPER_CHARIOT ->
                chariotTargets(position, position.getOrthogonalLines()) + cannonTargets() + horseTargets(position)

            ELEPHANT -> position.getDiagonal().filterNot { hasPieceAt(it) }
                .map { intermediate -> Position(2 * intermediate.x - position.x, 2 * intermediate.y - position.y) }
                .filter { if (color == WHITE) it.y < 5 else it.y > 4 }

            SOLDIER -> buildList {
                add(if (color == WHITE) position.getTop() else position.getBottom())
                if (if (color == WHITE) position.y >= 5 else position.y <= 4) {
                    add(position.getLeft())
                    add(position.getRight())
                }
            }

            GENERAL -> inPalace(position.getOrthogonal())

            ADVISOR -> inPalace(position.getDiagonal())
        }
        return movesToTargets(position, targets)
    }

    override fun copy(keepHistory: Boolean): XiangqiBoard =
        copyInto(XiangqiBoard(keepHistory = keepHistory, variant = variant), keepHistory)

    companion object {

        const val WIDTH = 9
        const val HEIGHT = 10
        const val DEFAULT_XIANGQI_START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0"

        /**
         * Manchu chess start FEN. Black pieces are set up as in standard xiangqi.
         * Red has only: general, 2 advisors, 2 elephants, 5 soldiers, and the super-chariot (M) at a1.
         * The super-chariot ('M'/'m') combines the powers of the chariot, horse, and cannon.
         */
        const val MANCHU_START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/9/9/M1BAKAB2 w - - 0 0"
        const val PRINT_SEPARATOR = " "
        const val UCI_MOVE_REGEX_PATTERN = "[a-i][0-9][a-i][0-9]"

        fun validateFen(fen: String) {
            try {
                XiangqiBoard(fen)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid FEN: $fen: ${e.message}")
            }
        }

        fun colorToPlay(fen: String): Color =
            XiangqiBoard(fen).colorToPlay()

        fun isMoveLegal(currentFen: String, move: String): Boolean =
            try {
                XiangqiBoard(currentFen).isLegalMove(parseMoveFromUci(move))
            } catch (_: Exception) {
                false
            }

        fun calculateNewFen(oldFen: String, move: String): String {
            val board = XiangqiBoard(oldFen)
            board.registerMove(move)
            return board.outputFen()
        }

        fun isCheckmated(fen: String): Boolean =
            XiangqiBoard(fen).isCheckmated()

        fun isStalemated(fen: String): Boolean =
            XiangqiBoard(fen).isStalemated()

        fun isInCheck(fen: String, color: Color): Boolean =
            XiangqiBoard(fen).isInCheck(color)

        fun moveToFens(movesHistory: List<String>, startFen: String = DEFAULT_XIANGQI_START_FEN, abridged: Boolean = false): List<String> {
            val board = XiangqiBoard(startFen)
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
