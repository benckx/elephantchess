package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.GameType

data class AdminUserInsightsDetailsResponse(
    val puzzle: ActivitySummary,
    val pvp: ActivitySummary,
    val pvpOver3: ActivitySummary,
    val pvb: ActivitySummary,
    val pvbOver3: ActivitySummary,
    val relatedUsers: List<RelatedUserEntry>,
    val sockPuppetGames: List<SockPuppetGameEntry>,
    val savedAnalysis: List<SavedAnalysisEntry>,
    val latestChatMessages: List<ChatMessageEntry>,
    val latestPageViews: List<PageViewEntry>,
    val latestSessions: List<SessionEntry>,
) {

    data class ActivitySummary(
        val firstPlayed: Long?,
        val lastPlayed: Long?,
        val totalPlayed: Int,
    )

    data class ChatMessageEntry(
        val gameId: String,
        val messageTime: Long,
        val content: String,
    )

    data class RelatedUserEntry(
        val userId: String,
        val username: String,
        val sharedIpCount: Int,
        val pvpGamesWithUser: Int,
        val lastSeen: Long?,
    )

    data class SockPuppetGameEntry(
        val gameId: String,
        val inviterUserId: String,
        val inviterUsername: String,
        val inviteeUserId: String,
        val inviteeUsername: String,
        val sharedRemoteAddressCount: Int,
        val sharedFingerprintCount: Int,
        val sharedRemoteAddress: String?,
        val sharedCountryName: String?,
        val sharedRegion: String?,
        val sharedCity: String?,
        val sharedOperatingSystemName: String?,
        val sharedAgentName: String?,
        val gameCreated: Long,
    )

    data class SavedAnalysisEntry(
        val analysisId: String,
        val name: String,
        val gameType: GameType?,
        val created: Long,
        val lastUpdated: Long,
    )

    data class PageViewEntry(val url: String, val time: Long)

    data class SessionEntry(
        val os: String,
        val agentName: String,
        val countryCode: String?,
        val countryName: String?,
        val region: String?,
        val city: String?,
        val remoteAddress: String,
        val otherUsersOnRemoteAddress: Int,
        val created: Long?,
        val updated: Long?,
    )
}
