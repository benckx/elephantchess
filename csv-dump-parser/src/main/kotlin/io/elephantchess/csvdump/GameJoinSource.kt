package io.elephantchess.csvdump

/**
 * Simplified source through which the second player joined the game, as written by
 * `ExtractPvpMovesToCsv`. A blank CSV value is represented as `null` at the [PvpGame] level.
 */
enum class GameJoinSource {
    LINK,
    LOBBY,
    DISCORD;

    companion object {

        fun fromCsvOrNull(value: String): GameJoinSource? =
            when (val trimmed = value.trim().uppercase()) {
                "" -> null
                "LINK" -> LINK
                "LOBBY" -> LOBBY
                "DISCORD" -> DISCORD
                else -> throw IllegalArgumentException("unknown game join source: '$trimmed'")
            }
    }
}
