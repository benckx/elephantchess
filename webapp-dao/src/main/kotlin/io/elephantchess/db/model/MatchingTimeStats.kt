package io.elephantchess.db.model

/**
 * Matching time (in seconds) between a game being created and joined, aggregated over a set of games.
 * [count] is the number of joined games considered; min/max/avg are null when no game was joined.
 */
data class MatchingTimeStats(
    val count: Int,
    val minSeconds: Int?,
    val maxSeconds: Int?,
    val avgSeconds: Double?,
)
