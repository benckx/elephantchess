package io.elephantchess.xiangqi

/**
 * Piece type independent of color.
 */
enum class AbstractPieceType(
    val uci: Char,
    val redLabel: Char,
    val blackLabel: Char,
    val maxLegal: Int = 2
) {

    GENERAL('k', '帅', '将', 1),
    ADVISOR('a', '仕', '士'),
    ELEPHANT('b', '相', '象'),
    HORSE('n', '傌', '馬'),
    CHARIOT('r', '俥', '車'),
    CANNON('c', '炮', '砲'),
    SOLDIER('p', '兵', '卒', 5);

}
