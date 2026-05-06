package io.elephantchess.db.model

import java.time.YearMonth

data class OnlineUsersStatsMonthly(
    val month: YearMonth,
    val minTotal: Int,
    val maxTotal: Int,
    val avgTotal: Int
)
