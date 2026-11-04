package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.AbstractPieceType.*
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.inferVariantFromFen
import io.elephantchess.xiangqi.Color.WHITE

open class XiangqiBoard(
    initFen: String = DEFAULT_START_FEN,
    keepHistory: Boolean = false,
    override val variant: Variant = inferVariantFromFen(initFen),
) : AbstractXiangqiBoard(initFen, keepHistory) {

    init {
        require(variant != Variant.JANGGI) { "Use JanggiBoard for Janggi rules" }
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
            targets.filter { if (color == WHITE) it.isInRedPalace() else it.isInBlackPalace() }

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
}
