package io.elephantchess.servicelayer.dto.admin

data class AdminUserInsightsRelatedUsersAndSockPuppetGamesResponse(
    val relatedUsers: List<AdminUserInsightsDetailsResponse.RelatedUserEntry>,
    val sockPuppetGames: List<AdminUserInsightsDetailsResponse.SockPuppetGameEntry>,
)
