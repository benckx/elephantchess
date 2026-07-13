package io.elephantchess.csvdump

/**
 * The change in a player's Elo rating over a game, from [before] to [after]. When the source CSV
 * did not contain both ratings (e.g. missing data), the whole change is represented as `null` at
 * the [PvpGame] level rather than with nullable fields here.
 */
data class PlayerEloUpdate(val before: Int, val after: Int) {

    val delta: Int get() = after - before

    companion object {

        /**
         * Builds a [PlayerEloUpdate] when both [before] and [after] are present, otherwise `null`.
         */
        fun of(before: Int?, after: Int?): PlayerEloUpdate? =
            if (before != null && after != null) PlayerEloUpdate(before, after) else null
    }
}
