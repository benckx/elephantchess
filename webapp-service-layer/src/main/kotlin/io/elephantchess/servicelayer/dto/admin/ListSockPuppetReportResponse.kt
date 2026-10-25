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
        val gameCreated: Long,
    )
}
