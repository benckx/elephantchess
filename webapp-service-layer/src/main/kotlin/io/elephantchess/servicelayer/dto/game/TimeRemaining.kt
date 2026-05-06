package io.elephantchess.servicelayer.dto.game

/**
 * In milliseconds
 */
data class TimeRemaining(
    val red: Long,
    val black: Long,
) {

    fun normalize(): TimeRemaining {
        return TimeRemaining(
            if (red < 0) 0 else red,
            if (black < 0) 0 else black
        )
    }

}
