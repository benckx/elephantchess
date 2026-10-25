package io.elephantchess.db.model

data class SockPuppetScanCandidateRecord(
    val gameId: String,
    val inviterUserId: String,
    val inviteeUserId: String,
)
