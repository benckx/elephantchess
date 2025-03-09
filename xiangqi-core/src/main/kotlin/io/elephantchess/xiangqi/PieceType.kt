package io.elephantchess.xiangqi

import io.elephantchess.utils.StringUtils.capitalize
import io.elephantchess.xiangqi.Color.Companion.UPPERCASE_COLOR

/**
 * A [AbstractPieceType] (which is colorless) + the [Color]
 */
data class PieceType(val abstractPieceType: AbstractPieceType, val color: Color) {

    fun letterNotation(): String {
        return if (color == UPPERCASE_COLOR) {
            abstractPieceType.uci.uppercase()
        } else {
            abstractPieceType.uci.toString()
        }
    }

    fun chineseCharacter(): Char {
        return if (color == Color.RED) {
            abstractPieceType.redLabel
        } else {
            abstractPieceType.blackLabel
        }
    }

    fun prettyName(): String {
        return "${color.name.capitalize()} ${abstractPieceType.name.capitalize()} (${chineseCharacter()})"
    }

    override fun toString(): String {
        return letterNotation()
    }

    companion object {

        fun fromChar(char: Char): PieceType {
            val abstractPieceType = AbstractPieceType.entries.find { it.uci == char.lowercaseChar() }
                ?: throw IllegalArgumentException("Can not parse $char")
            val color = Color.fromChar(char)
            return PieceType(abstractPieceType, color)
        }

    }

}
