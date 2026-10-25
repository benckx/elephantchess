package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.UserType

data class AdminUserInsightsData(
    val userId: String,
    val username: String,
    val email: String?,
    val userType: UserType?,
    val country: String?,
    val creationTime: Long?,
    val lastOnlineTime: Long?,
    val lastProfileUpdateTime: Long?,
    val emailConfirmedAtTime: Long?,
    val hasRoleAdmin: Boolean,
    val hasRoleEditor: Boolean,
    val puzzle: ActivitySummary,
    val pvp: ActivitySummary,
    val pvb: ActivitySummary,
    val latestPageViews: List<PageViewEntry>,
    val latestSessions: List<SessionEntry>,
) {

    data class ActivitySummary(
        val firstPlayed: Long?,
        val lastPlayed: Long?,
        val totalPlayed: Int,
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
        val created: Long?,
        val updated: Long?,
    )
}
