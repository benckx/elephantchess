package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.AbstractPieceType.GENERAL
import io.elephantchess.xiangqi.HalfMove.Companion.halfMoveIndexToFullMove

data class HistoricalMove(
    val index: Int,
    val physicalPiece: PhysicalPiece,
    val move: HalfMove,
    val capture: PhysicalPiece?,
    val isCheckingOpponent: Boolean,
    val attackMap: AttackMap,
) {

    init {
        val opponentColor = physicalPiece.color().reverse()
        require(capture == null || capture.color() == opponentColor)
        require(attackMap.keys.all { pieceAtPosition -> pieceAtPosition.color() == physicalPiece.color() })
        require(attackMap.values.flatten().all { pieceAtPosition -> pieceAtPosition.color() == opponentColor })
    }

    fun fullMove(): Int {
        return halfMoveIndexToFullMove(index)
    }

    fun isAttackingGeneral(piece: PhysicalPiece): Boolean {
        return attackMap
            .filter { (attackingPiece, _) -> attackingPiece.physicalPiece == piece }
            .any { (_, attackedPieces) ->
                attackedPieces.any { it.abstractPieceType() == GENERAL }
            }
    }

    override fun toString(): String {
        val fullMove = "[" + fullMove() + "] "
        val isCheckingStr = if (isCheckingOpponent) "+" else ""
        val captureStr = capture?.let { " takes ${it.pieceType}" } ?: ""
        var attackMapStr = attackMap
            .toList()
            .joinToString("; ") { (piece, attackedPieces) ->
                "$piece attacks ${attackedPieces.joinToString(", ")}"
            }

        if (attackMap.isNotEmpty()) {
            attackMapStr = " | $attackMapStr"
        }

        return "$fullMove${physicalPiece.pieceType}{$move}$isCheckingStr$captureStr$attackMapStr"
    }

}
