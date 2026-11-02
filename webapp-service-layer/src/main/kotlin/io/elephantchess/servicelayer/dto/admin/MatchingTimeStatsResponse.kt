package io.elephantchess.servicelayer.dto.admin

/**
 * Matching-time stats (milliseconds between a PvP game being created and joined) for a single month,
 * split by join source. The percentiles and min/max are null when no game was joined that month.
 */
data class MatchingTimeStatsResponse(
    val period: String,
    val lobbyJoinModes: MatchingTimeStat,
    val linkJoinSource: MatchingTimeStat,
    val lobbyJoinModesByCategory: Map<String, MatchingTimeStat>,
    val linkJoinSourceByCategory: Map<String, MatchingTimeStat>,
)

data class MatchingTimeStat(
    val count: Int,
    val minMillis: Long?,
    val medianMillis: Long?,
    val p90Millis: Long?,
    val p95Millis: Long?,
    val p99Millis: Long?,
    val maxMillis: Long?,
)
