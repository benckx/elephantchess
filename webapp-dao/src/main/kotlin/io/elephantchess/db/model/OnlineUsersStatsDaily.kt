package io.elephantchess.db.model

import java.time.LocalDate

data class OnlineUsersStatsDaily(
    val day: LocalDate,
    val minTotal: Int,
    val maxTotal: Int,
    val avgTotal: Int
)

