package io.elephantchess.servicelayer.dto.admin

data class AdminUserInsightsGameAndSavedAnalysisResponse(
    val puzzle: AdminUserInsightsDetailsResponse.ActivitySummary,
    val pvp: AdminUserInsightsDetailsResponse.ActivitySummary,
    val pvpOver3: AdminUserInsightsDetailsResponse.ActivitySummary,
    val pvb: AdminUserInsightsDetailsResponse.ActivitySummary,
    val pvbOver3: AdminUserInsightsDetailsResponse.ActivitySummary,
    val savedAnalysis: List<AdminUserInsightsDetailsResponse.SavedAnalysisEntry>,
)

data class AdminUserInsightsRelatedUsersAndSockPuppetGamesResponse(
    val relatedUsers: List<AdminUserInsightsDetailsResponse.RelatedUserEntry>,
    val sockPuppetGames: List<AdminUserInsightsDetailsResponse.SockPuppetGameEntry>,
)

data class AdminUserInsightsLatestChatMessagesResponse(val latestChatMessages: List<AdminUserInsightsDetailsResponse.ChatMessageEntry>)

data class AdminUserInsightsLatestPageViewsResponse(val latestPageViews: List<AdminUserInsightsDetailsResponse.PageViewEntry>)

data class AdminUserInsightsLatestSessionsResponse(val latestSessions: List<AdminUserInsightsDetailsResponse.SessionEntry>)
