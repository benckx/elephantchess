package io.elephantchess.db.model

data class SockPuppetDetectionSummaryRecord(
    val totalDetections: Int,
    val totalUsersInvolved: Int,
    val distinctUsersInvolved: Int,
    val reportedGamesLast30Days: Int,
    val totalPvpGamesLast30Days: Int,
    val reportedGamesRatioLast30Days: Double,
)
