package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.Board.Companion.HEIGHT
import io.elephantchess.xiangqi.Board.Companion.WIDTH

data class Position(val x: Int, val y: Int) {

    fun toUci() = "${fileIndexToLetter(x)}$y"

    fun toAlgebraic() = "${fileIndexToLetter(x)}${y + 1}"

    fun existsOnBoard() = (x in 0 until WIDTH) && (y in 0 until HEIGHT)

    fun getTop() = Position(x, y + 1)

    fun getBottom() = Position(x, y - 1)

    fun getLeft() = Position(x - 1, y)

    fun getRight() = Position(x + 1, y)

    fun getTopLeft() = Position(x - 1, y + 1)

    fun getTopRight() = Position(x + 1, y + 1)

    fun getBottomLeft() = Position(x - 1, y - 1)

    fun getBottomRight() = Position(x + 1, y - 1)

    fun getTopLine(): List<Position> {
        return if (y == HEIGHT || !existsOnBoard()) {
            listOf()
        } else {
            (y + 1 until HEIGHT).map { Position(x, it) }
        }
    }

    fun getBottomLine(): List<Position> {
        return if (y == 0 || !existsOnBoard()) {
            listOf()
        } else {
            (y - 1 downTo 0).map { Position(x, it) }
        }
    }

    fun getLeftLine(): List<Position> {
        return if (x == 0 || !existsOnBoard()) {
            listOf()
        } else {
            (x - 1 downTo 0).map { Position(it, y) }
        }
    }

    fun getRightLine(): List<Position> {
        return if (x == WIDTH || !existsOnBoard()) {
            listOf()
        } else {
            (x + 1 until WIDTH).map { Position(it, y) }
        }
    }

    fun getOrthogonalLines() = listOf(getTopLine(), getBottomLine(), getLeftLine(), getRightLine())

    fun getOrthogonal() = listOf(getTop(), getBottom(), getLeft(), getRight())

    fun getDiagonal() = listOf(getTopLeft(), getTopRight(), getBottomLeft(), getBottomRight())

    fun isInRedPalace() = x in 3..5 && y <= 2

    fun isInBlackPalace() = x in 3..5 && y >= HEIGHT - 3

    override fun toString(): String {
        return toAlgebraic()
    }

    companion object {

        /**
         * This assumes we use the standard board size
         */
        fun parsePositionFromUci(uci: String): Position {
            require(uci.length == 2) { "Can not parse $uci" }
            val char = uci[0].lowercase().toCharArray().first()
            val digit = uci[1]
            require(digit.isDigit()) { "Can not parse $uci, second argument must be a number" }
            require(CharRange('a', 'i').contains(char))
            return Position(letterToFileIndex(char), digit.digitToInt())
        }

        private fun letterToFileIndex(char: Char): Int {
            return char.code - 'a'.code
        }

        private fun fileIndexToLetter(i: Int): Char {
            return ('a'.code + i).toChar()
        }

        fun getAllPositions(): List<Position> {
            return (0 until WIDTH)
                .flatMap { x ->
                    (0 until HEIGHT).map { y -> Position(x, y) }
                }
        }

    }

}
