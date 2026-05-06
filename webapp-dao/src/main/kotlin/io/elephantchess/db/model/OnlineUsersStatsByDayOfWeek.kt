package io.elephantchess.db.model

data class OnlineUsersStatsByDayOfWeek(
    val dayOfWeek: Int, // 0 = Sunday, 1 = Monday, etc.
    val minTotal: Int,
    val maxTotal: Int,
    val avgTotal: Int
)

