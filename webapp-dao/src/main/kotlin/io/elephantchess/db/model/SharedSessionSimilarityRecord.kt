package io.elephantchess.db.model

data class SharedSessionSimilarityRecord(
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
)
