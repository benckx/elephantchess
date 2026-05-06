package io.elephantchess.db.model

import kotlin.time.Instant

data class PuzzleLeaderboardRecord(
    val userId: String,
    val username: String,
    val countryCode: String?,
    val currentRating: Int,
    val maxRating: Int,
    val totalPlayed: Int,
    val lastPlayed: Instant,
)
