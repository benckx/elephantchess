package io.elephantchess.csvdump

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Parsed time control of a game.
 *
 * There are two kinds of control, distinguished by [isPerMove]:
 *  - regular controls (written as `"<base>+<increment>"`), where each side gets [base] on the
 *    clock plus a Fischer [increment] added after every move;
 *  - per-move controls (written as `"<base>/move"`), used by correspondence games, where each move
 *    simply has [base] to be played and there is no increment ([increment] is `null`).
 */
data class TimeControl(
    val base: Duration,
    val increment: Duration?,
    val isPerMove: Boolean,
) {

    companion object {

        fun fromCsv(value: String): TimeControl {
            val trimmed = value.trim()
            return when {
                trimmed.endsWith("/move") ->
                    TimeControl(trimmed.removeSuffix("/move").toInt().seconds, increment = null, isPerMove = true)

                "+" in trimmed -> {
                    val (base, increment) = trimmed.split("+", limit = 2)
                    TimeControl(base.toInt().seconds, increment.toInt().seconds, isPerMove = false)
                }

                else -> throw IllegalArgumentException("unknown time control format: '$value'")
            }
        }
    }
}
