package io.elephantchess.servicelayer.dto.admin

/**
 * Matching-time stats (seconds between a PvP game being created and joined) for a single month,
 * split by join source. min/max/avg are null when no game was joined that month.
 */
data class MatchingTimeStatsResponse(
    val period: String,
    val lobbyJoinModes: MatchingTimeStat,
    val linkJoinSource: MatchingTimeStat,
)

data class MatchingTimeStat(
    val count: Int,
    val minSeconds: Int?,
    val maxSeconds: Int?,
    val avgSeconds: Double?,
)
