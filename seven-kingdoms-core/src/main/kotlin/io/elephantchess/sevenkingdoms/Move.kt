package io.elephantchess.sevenkingdoms

data class Move(
    val from: Position,
    val to: Position
) {

    val uci: String
        get() = "${from.uci}${to.uci}"

    val algebraic: String
        get() = "${from.algebraic}${to.algebraic}"

    override fun toString() = uci

    companion object {

        fun parseMoveFromUci(uci: String): Move {
            val firstLetter = uci[0]
            if (!firstLetter.isLetter()) {
                throw IllegalArgumentException("Invalid UCI: $uci")
            }

            val secondLetterIndex = uci.substring(1).indexOfFirst { it.isLetter() } + 1
            if (secondLetterIndex == 0) {
                throw IllegalArgumentException("Invalid UCI: $uci")
            }

            val secondLetter = uci[secondLetterIndex]
            if (!secondLetter.isLetter()) {
                throw IllegalArgumentException("Invalid UCI: $uci")
            }

            val from = Position(
                x = firstLetter - 'a',
                y = uci.substring(1, secondLetterIndex).toInt()
            )

            val to = Position(
                x = secondLetter - 'a',
                y = uci.substring(secondLetterIndex + 1).toInt()
            )

            if (!from.isOnBoard || !to.isOnBoard) {
                throw IllegalArgumentException("Invalid UCI: $uci, position out of board")
            }

            return Move(from, to)
        }

    }

}
