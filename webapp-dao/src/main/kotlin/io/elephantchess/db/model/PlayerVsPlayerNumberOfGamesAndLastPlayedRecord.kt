package io.elephantchess.db.model

import io.elephantchess.model.TimeControlCategory
import kotlin.time.Instant

data class PlayerVsPlayerNumberOfGamesAndLastPlayedRecord(
    val userId: String,
    val category: TimeControlCategory,
    val totalPlayed: Int,
    val lastPlayed: Instant,
)
