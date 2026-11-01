package io.elephantchess.xiangqi

import java.util.*

// TODO: this is used in DTO - is it best practice?
enum class Color {

    WHITE,
    BLACK,
    ;

    fun isWhiteSide(): Boolean =
        this == WHITE

    fun toUci(): Char =
        when (this) {
            WHITE -> 'w'
            BLACK -> 'b'
        }

    fun reverse(): Color =
        when (this) {
            WHITE -> BLACK
            BLACK -> WHITE
        }

    companion object {

        val UPPERCASE_COLOR = WHITE
        val LOWERCASE_COLOR = BLACK

        fun fromChar(char: Char): Color =
            if (char.isLowerCase()) {
                LOWERCASE_COLOR
            } else {
                UPPERCASE_COLOR
            }

        /**
         * Color that has to play after the given half move position
         */
        fun fromPosition(position: Int): Color =
            if (position % 2 == 0) BLACK else WHITE

        fun random(): Color =
            if (Random().nextBoolean()) BLACK else WHITE
    }
}
