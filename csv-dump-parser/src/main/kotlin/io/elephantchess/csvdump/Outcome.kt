package io.elephantchess.csvdump

/**
 * Result of a game. A blank CSV value (e.g. an unfinished game) is represented as `null` at the
 * [PvpGame] level rather than as an enum constant.
 */
enum class Outcome {
    WHITE_WINS,
    BLACK_WINS,
    DRAW,
    ;

    companion object {

        fun fromCsvOrNull(value: String): Outcome? =
            when (val trimmed = value.trim().uppercase()) {
                "" -> null
                "RED_WINS" -> WHITE_WINS
                "WHITE_WINS" -> WHITE_WINS
                "BLACK_WINS" -> BLACK_WINS
                "DRAW" -> DRAW
                else -> throw IllegalArgumentException("unknown outcome: '$trimmed'")
            }
    }
}
