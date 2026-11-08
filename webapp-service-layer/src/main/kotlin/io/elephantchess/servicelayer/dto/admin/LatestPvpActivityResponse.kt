package io.elephantchess.servicelayer.dto.admin

data class LatestPvpActivityResponse(
    val latestPvpActivity: Long?,
    val latestPvp3Activity: Long?,
    val latestManchu3Activity: Long?,
    val latestXiangqi3Activity: Long?,
)
