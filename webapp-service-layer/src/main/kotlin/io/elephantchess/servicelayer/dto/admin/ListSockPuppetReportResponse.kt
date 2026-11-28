package io.elephantchess.servicelayer.dto.admin

data class ListSockPuppetReportResponse(val summary: Summary, val entries: List<Entry>) {
    data class Summary(
        val totalDetections: Int,
        val totalUsersInvolved: Int,
        val distinctUsersInvolved: Int,
        val reportedGamesLast30Days: Int,
        val totalPvpGamesLast30Days: Int,
        val reportedGamesPercentageLast30Days: Double,
    )

    data class Entry(
        val gameId: String,
        val inviterUserId: String,
        val inviterUsername: String,
        val inviteeUserId: String,
        val inviteeUsername: String,
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
        val gameCreated: Long,
    )
}
