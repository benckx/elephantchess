package io.elephantchess.servicelayer.dto.admin

data class LatestPvbActivityResponse(
    val latestPvbActivity: Long?,
    val latestPvb3Activity: Long?,
    val latestFairy3Activity: Long?,
    val latestManchu3Activity: Long?,
    val latestJanggi3Activity: Long?,
)
