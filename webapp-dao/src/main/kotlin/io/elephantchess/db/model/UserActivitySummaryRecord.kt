package io.elephantchess.db.model

import kotlin.time.Instant

data class UserActivitySummaryRecord(
    val firstPlayed: Instant?,
    val lastPlayed: Instant?,
    val totalPlayed: Int,
)
