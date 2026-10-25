package io.elephantchess.servicelayer.dto.admin

data class ListSockPuppetReportResponse(val summary: Summary, val entries: List<Entry>) {
    data class Summary(
        val totalDetections: Int,
        val distinctUsersInvolved: Int,
        val distinctInviters: Int,
        val distinctInvitees: Int,
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
