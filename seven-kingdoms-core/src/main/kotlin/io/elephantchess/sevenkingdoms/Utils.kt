package io.elephantchess.sevenkingdoms

import io.elephantchess.sevenkingdoms.AbstractPieceType.Companion.getPieceTypeByUci
import io.elephantchess.sevenkingdoms.Color.Companion.getColorFromFenColorChar
import io.elephantchess.sevenkingdoms.Position.Companion.BOARD_SIZE

fun <A, B> Map<A, List<B>>.filterForLongestListSize(): List<Pair<A, List<B>>> {
    if (isEmpty()) {
        return emptyList()
    }

    val unfilteredCandidates = toList().sortedBy { (_, items) -> items.size }
    val longestListSize = unfilteredCandidates.last().second.size
    return unfilteredCandidates.filter { (_, pieces) -> pieces.size == longestListSize }.toList()
}

fun parseFenToPiecesAtPositions(fen: String): List<PieceAtPosition> {
    fun parseFenRank(fenRank: String, y: Int): List<PieceAtPosition> {
        var isParsingNumeral = false
        var numberBuilder = ""
        var skipNextChar = false
        var x = 0
        val pieces = mutableListOf<PieceAtPosition>()

        for (i in fenRank.indices) {
            val char = fenRank[i]

            if (char.isDigit()) {
                isParsingNumeral = true
                numberBuilder += char
            } else {
                if (isParsingNumeral) {
                    x += numberBuilder.toInt()
                    isParsingNumeral = false
                    numberBuilder = ""
                }

                if (!skipNextChar) {
                    val color = getColorFromFenColorChar(char)
                    if (i == fenRank.length - 1) {
                        throw IllegalArgumentException("Missing piece type at the end of the FEN rank")
                    }

                    val nextChar = fenRank[i + 1]
                    val pieceType = getPieceTypeByUci(nextChar)

                    val piece = Piece(color, pieceType)
                    val position = Position(x, y)
                    val pieceAtPosition = PieceAtPosition(piece, position)
                    pieces.add(pieceAtPosition)
                    skipNextChar = true
                    x += 1
                } else {
                    skipNextChar = false
                }
            }
        }

        return pieces
    }

    val fenRanks = fen.split("/")
    val pieces = mutableListOf<PieceAtPosition>()
    for (i in fenRanks.indices) {
        pieces.addAll(parseFenRank(fenRanks[i], BOARD_SIZE - i - 1))
    }

    return pieces
}
