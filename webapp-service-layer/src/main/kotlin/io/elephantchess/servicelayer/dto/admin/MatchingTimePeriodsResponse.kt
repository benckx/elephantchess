package io.elephantchess.servicelayer.dto.admin

/**
 * Available months (format "YYYY-MM") for which matching-time stats can be requested.
 * The admin page iterates over these and loads one month at a time.
 */
data class MatchingTimePeriodsResponse(
    val periods: List<String>,
)
