package io.elephantchess.db.model

import io.elephantchess.model.TimeControlCategory

data class PlayerVsPlayerOutcomeStatsPerCategoryRecord(
    val category: TimeControlCategory,
    val wins: Int,
    val losses: Int,
    val draws: Int,
)
