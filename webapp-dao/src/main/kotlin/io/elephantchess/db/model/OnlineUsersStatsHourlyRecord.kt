package io.elephantchess.db.model

data class OnlineUsersStatsHourlyRecord(
    val hourOfDay: Int,
    val minTotal: Int,
    val maxTotal: Int,
    val avgTotal: Int
)

