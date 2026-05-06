package io.elephantchess.sevenkingdoms

data class PieceAtPosition(
    val piece: Piece,
    val position: Position,
) {

    val color
        get() = piece.color

    val abstractPieceType
        get() = piece.abstractPieceType

    fun copyWithColor(color: Color) =
        PieceAtPosition(piece.copy(color = color), position.copy())

    override fun toString(): String {
        return "$piece at $position"
    }

}
