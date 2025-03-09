package io.elephantchess.xiangqi

data class PieceAtPosition(
    val physicalPiece: PhysicalPiece,
    val position: Position,
) {

    fun color() = physicalPiece.color()

    fun abstractPieceType() = physicalPiece.pieceType.abstractPieceType

    override fun toString(): String {
        val letter = physicalPiece.pieceType.letterNotation()
        val algebraic = position.toAlgebraic()
        return "$letter{$algebraic}"
    }

}
