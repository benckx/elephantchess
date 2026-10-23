package io.elephantchess.db.model

/**
 * Matching time (in seconds) between a game being created and joined, aggregated over a set of games.
 * [count] is the number of joined games considered; the percentiles and min/max are null when no game
 * was joined. Percentiles (p50/median, p90, p95, p99) make the distribution readable despite outliers.
 */
data class MatchingTimeStats(
    val count: Int,
    val minSeconds: Int?,
    val medianSeconds: Int?,
    val p90Seconds: Int?,
    val p95Seconds: Int?,
    val p99Seconds: Int?,
    val maxSeconds: Int?,
)
