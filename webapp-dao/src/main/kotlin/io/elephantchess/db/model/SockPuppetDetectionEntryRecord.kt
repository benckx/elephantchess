package io.elephantchess.db.model

import kotlin.time.Instant

data class SockPuppetDetectionEntryRecord(
    val gameId: String,
    val inviterUserId: String,
    val inviteeUserId: String,
    val sharedRemoteAddressCount: Int,
    val sharedFingerprintCount: Int,
    val gameCreated: Instant,
)
