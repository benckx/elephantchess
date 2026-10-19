package io.elephantchess.servicelayer.dto.global

data class GlobalUserStatsResponse(
    val totalUsers: Int,
    val recentlyActiveUsers: Int,
    val onlineUsers: Int,
)
