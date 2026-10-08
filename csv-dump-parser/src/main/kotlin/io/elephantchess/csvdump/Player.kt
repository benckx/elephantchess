package io.elephantchess.csvdump

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * One side (red or black) of a PvP game, grouping the player's identity and the facts the CSV
 * records about the account itself: account age at game time and user type.
 */
data class Player(
    val name: String,
    val accountAge: Duration?,
    val userType: UserType,
) {

    /**
     * Approximates when this account was created by subtracting its [accountAge] from the start of
     * the game, rounded to the nearest hour. Returns `null` when the account age is unknown. The
     * result is only as precise as the source data, which itself rounds the age to whole days (or
     * hours for very young accounts).
     */
    fun approximateAccountCreation(gameStart: Instant): Instant? =
        accountAge?.let { age -> (gameStart - age).roundToNearestHour() }

    private fun Instant.roundToNearestHour(): Instant {
        val secondsPerHour = 60L * 60L
        val rounded = (epochSeconds + secondsPerHour / 2).floorDiv(secondsPerHour) * secondsPerHour
        return Instant.fromEpochSeconds(rounded)
    }

    companion object {

        /**
         * Parses the account age as written by `ExtractPvpMovesToCsv`, e.g. `"27d"` or `"5h"`.
         * A blank value (missing data) yields `null`.
         */
        fun parseAccountAge(raw: String): Duration? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null
            val amount = trimmed.dropLast(1).toLong()
            return when (trimmed.last()) {
                'd' -> amount.days
                'h' -> amount.hours
                else -> throw IllegalArgumentException("unknown account age format: '$raw'")
            }
        }
    }

}
