package io.elephantchess.xiangqi

/**
 * To make the distinction between e.g. the left-side red rook and the right-side red rook,
 * while [PieceType] does not make this distinction (since it's the same red rook piece type at that level).
 */
data class PhysicalPiece(
    val pieceType: PieceType,
    val initPosition: Position
) {

    fun color(): Color = pieceType.color

    override fun toString(): String {
        val letter = pieceType.letterNotation()
        val algebraic = initPosition.toAlgebraic()
        return "$letter{$algebraic}"
    }

}
