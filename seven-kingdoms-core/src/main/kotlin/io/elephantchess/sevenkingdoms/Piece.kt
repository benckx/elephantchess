package io.elephantchess.sevenkingdoms

data class Piece(
    val color: Color,
    val abstractPieceType: AbstractPieceType,
) {

    override fun toString(): String {
        return "Piece{${color} ${abstractPieceType}}"
    }

}
