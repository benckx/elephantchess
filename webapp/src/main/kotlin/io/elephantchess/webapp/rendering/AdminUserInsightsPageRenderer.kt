package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.KtorHtmlBuilderTagResolver
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsData
import io.elephantchess.servicelayer.services.admin.AdminUserInsightsService
import io.elephantchess.utils.di.KoinSingleton
import io.ktor.http.*
import kotlinx.html.*

@KoinSingleton(eager = true)
class AdminUserInsightsPageRenderer(
    private val htmlRenderer: HtmlRenderer,
    private val adminUserInsightsService: AdminUserInsightsService,
) {

    suspend fun renderUserInsightsPage(identifier: String): String {
        val data = adminUserInsightsService.fetchUserInsights(identifier)
        val encodedIdentifier = identifier.encodeURLPath()
        val pageViewsUrl = "/admin/user/$encodedIdentifier/page-views"
        val chatMessagesUrl = "/admin/user/$encodedIdentifier/chat-messages"

        return htmlRenderer.renderHtml(
            templatePath = "/templates/admin/admin_user_insights.html",
            canonicalPath = "/admin/user/$encodedIdentifier",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("username", data.username),
                publicProfileLinkTagResolver(data),
                userMetadataTableTagResolver(data),
                gameActivityTableTagResolver(data),
                relatedUsersTableTagResolver(data),
                sockPuppetGamesTableTagResolver(data),
                savedAnalysisTableTagResolver(data),
                latestChatMessagesTagResolver(data),
                fullChatMessagesLinkTagResolver(data, chatMessagesUrl),
                latestPageViewsTagResolver(data),
                fullPageViewsLinkTagResolver(data, pageViewsUrl),
                latestSessionsTagResolver(data),
            ),
        )
    }

    private fun publicProfileLinkTagResolver(data: AdminUserInsightsData) =
        KtorHtmlBuilderTagResolver("admin_user_public_profile_link") {
            if (data.userType == UserType.AUTHENTICATED && data.username.isNotBlank()) {
                a(href = "/@/${data.username.encodeURLPath()}") {
                    id = "admin-user-insights-public-profile-link"
                    +"public profile"
                }
            } else {
                span {
                    id = "admin-user-insights-public-profile-link"
                    +"No public profile for guest users."
                }
            }
        }

    suspend fun renderUserPageViewsPage(identifier: String): String {
        val data = adminUserInsightsService.fetchUserInsights(identifier)
        val encodedIdentifier = identifier.encodeURLPath()

        return htmlRenderer.renderHtml(
            templatePath = "/templates/admin/admin_user_insights_page_views.html",
            canonicalPath = "/admin/user/$encodedIdentifier/page-views",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("username", data.username),
                SimpleValueTagResolver("identifier", identifier),
                SimpleValueTagResolver("encoded_identifier", encodedIdentifier),
            ),
        )
    }

    suspend fun renderUserChatMessagesPage(identifier: String): String {
        val data = adminUserInsightsService.fetchUserInsights(identifier)
        val encodedIdentifier = identifier.encodeURLPath()

        return htmlRenderer.renderHtml(
            templatePath = "/templates/admin/admin_user_insights_chat_messages.html",
            canonicalPath = "/admin/user/$encodedIdentifier/chat-messages",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("username", data.username),
                SimpleValueTagResolver("identifier", identifier),
                SimpleValueTagResolver("encoded_identifier", encodedIdentifier),
            ),
        )
    }

    private fun userMetadataTableTagResolver(data: AdminUserInsightsData) =
        KtorHtmlBuilderTagResolver("admin_user_metadata_table") {
            table("nice-table nice-table-striped") {
                id = "admin-user-metadata-table"
                metadataRow("user id", data.userId)
                metadataRow("username", data.username)
                metadataRow("email", data.email ?: "n/a")
                metadataRow("user type", data.userType?.name ?: "n/a")
                metadataRow("country", data.country ?: "n/a")
                metadataRow("created", formatTimestampWithRelativeAge(data.creationTime))
                metadataRow("last online", formatTimestampWithRelativeAge(data.lastOnlineTime))
                metadataRow("last profile update", formatTimestampUtc(data.lastProfileUpdateTime))
                metadataRow("email confirmed", formatTimestampUtc(data.emailConfirmedAtTime))
                metadataRow("email validity", formatEmailValidityStatus(data))
                metadataRow("role admin", data.hasRoleAdmin.toString())
                metadataRow("role editor", data.hasRoleEditor.toString())
            }
        }

    private fun gameActivityTableTagResolver(data: AdminUserInsightsData) =
        KtorHtmlBuilderTagResolver("admin_user_game_activity_table") {
            table("nice-table nice-table-striped") {
                id = "admin-user-game-activity-table"
                tr {
                    th { +"mode" }
                    th { +"first played" }
                    th { +"last played" }
                    th { +"total played" }
                }
                activityRow("puzzles", data.puzzle)
                activityRow("PvP", data.pvp)
                activityRow("PvP > 3", data.pvpOver3)
                activityRow("PvB", data.pvb)
                activityRow("PvB > 3", data.pvbOver3)
            }
        }

    private fun relatedUsersTableTagResolver(data: AdminUserInsightsData) =
        KtorHtmlBuilderTagResolver("admin_user_related_users_table") {
            if (data.relatedUsers.isEmpty()) {
                p("empty-block-placeholder") { +"No related users detected from recent sessions." }
            } else {
                table("nice-table nice-table-striped") {
                    id = "admin-user-related-users-table"
                    tr {
                        th { +"user" }
                        th { +"shared IPs" }
                        th { +"PvP together" }
                        th { +"last seen" }
                    }
                    data.relatedUsers.forEach { entry ->
                        tr {
                            td {
                                a(href = "/admin/user/${entry.userId.encodeURLPath()}") {
                                    +formatRelatedUserDisplay(entry)
                                }
                            }
                            td { +entry.sharedIpCount.toString() }
                            td { +formatPvpTogether(entry.pvpGamesWithUser) }
                            td { +formatTimestampUtc(entry.lastSeen) }
                        }
                    }
                }
            }
        }

    private fun sockPuppetGamesTableTagResolver(data: AdminUserInsightsData) =
        KtorHtmlBuilderTagResolver("admin_user_sock_puppet_games_table") {
            if (data.sockPuppetGames.isEmpty()) {
                p("empty-block-placeholder") { +"No game sock puppet reports found." }
            } else {
                table("nice-table nice-table-striped") {
                    id = "admin-user-sock-puppet-games-table"
                    tr {
                        th { +"PvP game" }
                        th { +"inviter" }
                        th { +"invitee" }
                        th { +"shared IPs" }
                        th { +"shared session fingerprints" }
                        th { +"shared IP" }
                        th { +"country" }
                        th { +"region" }
                        th { +"city" }
                        th { +"os" }
                        th { +"agent" }
                        th { +"game created" }
                    }
                    data.sockPuppetGames.forEach { entry ->
                        tr {
                            td {
                                a(href = "/game?id=${entry.gameId.encodeURLQueryComponent()}") {
                                    +entry.gameId
                                }
                            }
                            td {
                                a(href = "/admin/user/${entry.inviterUserId.encodeURLPath()}") {
                                    +formatUsernameAndId(entry.inviterUsername, entry.inviterUserId)
                                }
                            }
                            td {
                                a(href = "/admin/user/${entry.inviteeUserId.encodeURLPath()}") {
                                    +formatUsernameAndId(entry.inviteeUsername, entry.inviteeUserId)
                                }
                            }
                            td { +entry.sharedRemoteAddressCount.toString() }
                            td { +entry.sharedFingerprintCount.toString() }
                            td { greyOutText(entry.sharedRemoteAddress ?: "n/a") }
                            td { greyOutText(entry.sharedCountryName ?: "n/a") }
                            td { greyOutText(entry.sharedRegion ?: "n/a") }
                            td { greyOutText(entry.sharedCity ?: "n/a") }
                            td { greyOutText(entry.sharedOperatingSystemName ?: "n/a") }
                            td { greyOutText(entry.sharedAgentName ?: "n/a") }
                            td { +formatTimestampUtc(entry.gameCreated) }
                        }
                    }
                }
            }
        }

    private fun savedAnalysisTableTagResolver(data: AdminUserInsightsData) =
        KtorHtmlBuilderTagResolver("admin_user_saved_analysis_table") {
            if (data.savedAnalysis.isEmpty()) {
                p("empty-block-placeholder") { +"No saved analysis found." }
            } else {
                table("nice-table nice-table-striped") {
                    id = "admin-user-saved-analysis-table"
                    tr {
                        th { +"name" }
                        th { +"game type" }
                        th { +"created" }
                        th { +"last updated" }
                    }
                    data.savedAnalysis.forEach { entry ->
                        tr {
                            td {
                                a(href = "/analysis?id=${entry.analysisId.encodeURLQueryComponent()}") {
                                    +entry.name
                                }
                            }
                            td { greyOutText(entry.gameType?.name ?: "n/a") }
                            td { +formatTimestampUtc(entry.created) }
                            td { +formatTimestampWithRelativeAge(entry.lastUpdated) }
                        }
                    }
                }
            }
        }

    private fun latestChatMessagesTagResolver(data: AdminUserInsightsData) =
        KtorHtmlBuilderTagResolver("admin_user_latest_chat_messages_table") {
            if (data.latestChatMessages.isEmpty()) {
                p("empty-block-placeholder") { +"No chat messages found." }
            } else {
                table("nice-table nice-table-striped") {
                    id = "admin-user-latest-chat-messages-table"
                    tr {
                        th { +"game id" }
                        th { +"message" }
                        th { +"time" }
                    }
                    data.latestChatMessages.forEach { entry ->
                        tr {
                            td {
                                a(href = "/game?id=${entry.gameId.encodeURLQueryComponent()}") {
                                    +entry.gameId
                                }
                            }
                            td { +entry.content }
                            td { +formatTimestampUtc(entry.messageTime) }
                        }
                    }
                }
            }
        }

    private fun latestPageViewsTagResolver(data: AdminUserInsightsData) =
        KtorHtmlBuilderTagResolver("admin_user_latest_page_views_table") {
            if (data.latestPageViews.isEmpty()) {
                p("empty-block-placeholder") { +"No page views found." }
            } else {
                table("nice-table nice-table-striped") {
                    id = "admin-user-latest-page-views-table"
                    tr {
                        th { +"url" }
                        th { +"time" }
                    }
                    data.latestPageViews.forEach { pageViewEntry ->
                        tr {
                            td {
                                a(href = pageViewEntry.url) {
                                    +pageViewEntry.url
                                }
                            }
                            td { +formatTimestampUtc(pageViewEntry.time) }
                        }
                    }
                }
            }
        }

    private fun fullChatMessagesLinkTagResolver(data: AdminUserInsightsData, href: String) =
        KtorHtmlBuilderTagResolver("admin_user_chat_messages_full_link") {
            if (data.latestChatMessages.isEmpty()) {
                span("disabled-section-link") {
                    id = "admin-user-chat-messages-full-link-disabled"
                    +"view all chat messages"
                }
            } else {
                a(href = href) {
                    id = "admin-user-chat-messages-full-link"
                    +"view all chat messages"
                }
            }
        }

    private fun fullPageViewsLinkTagResolver(data: AdminUserInsightsData, href: String) =
        KtorHtmlBuilderTagResolver("admin_user_page_views_full_link") {
            if (data.latestPageViews.isEmpty()) {
                span("disabled-section-link") {
                    id = "admin-user-page-views-full-link-disabled"
                    +"view all page views"
                }
            } else {
                a(href = href) {
                    id = "admin-user-page-views-full-link"
                    +"view all page views"
                }
            }
        }

    private fun latestSessionsTagResolver(data: AdminUserInsightsData) =
        KtorHtmlBuilderTagResolver("admin_user_latest_sessions_table") {
            if (data.latestSessions.isEmpty()) {
                p("empty-block-placeholder") { +"No sessions found." }
            } else {
                table("nice-table nice-table-striped") {
                    id = "admin-user-latest-sessions-table"
                    tr {
                        th { +"os" }
                        th { +"agent" }
                        th { +"country" }
                        th { +"region" }
                        th { +"city" }
                        th { +"IP" }
                        th { +"sock puppet" }
                        th { +"created" }
                        th { +"updated" }
                    }
                    data.latestSessions.forEach { entry ->
                        tr {
                            td { +entry.os }
                            td { +entry.agentName }
                            td { greyOutText(entry.countryName ?: entry.countryCode ?: "n/a") }
                            td { greyOutText(entry.region ?: "n/a") }
                            td { greyOutText(entry.city ?: "n/a") }
                            td { +entry.remoteAddress }
                            td { +formatSockPuppetSignal(entry.otherUsersOnRemoteAddress) }
                            td { +formatTimestampUtc(entry.created) }
                            td { +formatTimestampUtc(entry.updated) }
                        }
                    }
                }
            }
        }

    private fun TABLE.metadataRow(label: String, value: String) {
        tr {
            th { +label }
            td { greyOutText(value) }
        }
    }

    private fun TABLE.activityRow(label: String, summary: AdminUserInsightsData.ActivitySummary) {
        tr {
            td { +label }
            td { greyOutText(formatTimestampUtc(summary.firstPlayed)) }
            td { greyOutText(formatTimestampWithRelativeAge(summary.lastPlayed)) }
            td { greyOutZeroIfNoActivity(summary) }
        }
    }

    private fun TD.greyOutText(value: String) {
        if (value == "n/a") {
            span("na-value") { +value }
        } else {
            +value
        }
    }

    private fun TD.greyOutZeroIfNoActivity(summary: AdminUserInsightsData.ActivitySummary) {
        if (summary.totalPlayed == 0 && summary.firstPlayed == null && summary.lastPlayed == null) {
            span("na-value") { +"0" }
        } else {
            +summary.totalPlayed.toString()
        }
    }

    private fun formatEmailValidityStatus(data: AdminUserInsightsData): String {
        val status = data.emailValidityStatus ?: return "n/a"
        return status
            .name
            .lowercase()
            .replace('_', ' ')
    }

    private fun formatSockPuppetSignal(otherUsersOnRemoteAddress: Int): String =
        when {
            otherUsersOnRemoteAddress <= 0 -> "no"
            otherUsersOnRemoteAddress == 1 -> "possible (1 other user on IP)"
            else -> "possible ($otherUsersOnRemoteAddress other users on IP)"
        }

    private fun formatPvpTogether(totalGames: Int): String =
        if (totalGames <= 0) {
            "no"
        } else {
            "yes ($totalGames)"
        }

    private fun formatRelatedUserDisplay(entry: AdminUserInsightsData.RelatedUserEntry): String =
        if (entry.username == entry.userId) {
            "#${entry.userId}"
        } else {
            "${entry.username} (${entry.userId})"
        }

    private fun formatUsernameAndId(username: String, userId: String): String =
        if (username == userId) "#$userId" else "$username ($userId)"

    private fun formatTimestampWithRelativeAge(epochMillis: Long?): String {
        val formattedTimestamp = formatTimestampUtc(epochMillis)
        val relative = formatTimestampToRelativeTime(epochMillis)
        return if (epochMillis == null) formattedTimestamp else "$formattedTimestamp ($relative)"
    }
}
