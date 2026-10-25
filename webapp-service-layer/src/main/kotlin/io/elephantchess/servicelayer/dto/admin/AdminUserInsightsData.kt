package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.user.EmailValidityStatus

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
    val emailValidityStatus: EmailValidityStatus?,
    val hasRoleAdmin: Boolean,
    val hasRoleEditor: Boolean,
    val puzzle: ActivitySummary,
    val pvp: ActivitySummary,
    val pvpOver3: ActivitySummary,
    val pvb: ActivitySummary,
    val pvbOver3: ActivitySummary,
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
        val index: Int,
        val messageTime: Long,
        val content: String,
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
