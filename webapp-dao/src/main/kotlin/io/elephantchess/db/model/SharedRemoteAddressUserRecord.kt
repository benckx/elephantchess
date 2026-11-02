package io.elephantchess.db.model

import kotlin.time.Instant

data class SharedRemoteAddressUserRecord(
    val userId: String,
    val sharedRemoteAddressCount: Int,
    val lastSeen: Instant?,
)
