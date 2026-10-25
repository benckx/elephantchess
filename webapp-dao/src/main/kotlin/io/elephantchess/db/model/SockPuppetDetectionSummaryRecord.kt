package io.elephantchess.db.model

data class SockPuppetDetectionSummaryRecord(
    val totalDetections: Int,
    val distinctUsersInvolved: Int,
    val distinctInviters: Int,
    val distinctInvitees: Int,
)
