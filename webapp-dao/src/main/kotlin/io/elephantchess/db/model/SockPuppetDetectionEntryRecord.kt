package io.elephantchess.db.model

import kotlin.time.Instant

data class SockPuppetDetectionEntryRecord(
    val gameId: String,
    val inviterUserId: String,
    val inviteeUserId: String,
    val sharedRemoteAddressCount: Int,
    val sharedFingerprintCount: Int,
    val sharedRemoteAddress: String?,
    val sharedCountryCode: String?,
    val sharedCountryName: String?,
    val sharedRegion: String?,
    val sharedCity: String?,
    val sharedOperatingSystemName: String?,
    val sharedAgentName: String?,
    val sharedAgentClass: String?,
    val sharedUserAgent: String?,
    val gameCreated: Instant,
)
