package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.KtorHtmlBuilderTagResolver
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsResponse
import io.elephantchess.servicelayer.services.admin.AdminUserInsightsService
import io.elephantchess.utils.di.KoinSingleton
import io.ktor.http.encodeURLPath
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter

@KoinSingleton(eager = true)
class AdminUserInsightsPageRenderer(
    private val htmlRenderer: HtmlRenderer,
    private val adminUserInsightsService: AdminUserInsightsService,
) {

    suspend fun renderUserInsightsPage(username: String): String {
        val data = adminUserInsightsService.fetchUserInsights(username)
        val encodedUsername = data.username.encodeURLPath()

        return htmlRenderer.renderHtml(
            templatePath = "/templates/admin/admin_user_insights.html",
            canonicalPath = "/admin/user/$encodedUsername",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("username", data.username),
                SimpleValueTagResolver("public_profile_url", "/@/$encodedUsername"),
                userMetadataTableTagResolver(data),
                gameActivityTableTagResolver(data),
                latestPageViewsTagResolver(data),
                latestSessionsTagResolver(data),
            ),
        )
    }

    private fun userMetadataTableTagResolver(data: AdminUserInsightsResponse) =
        KtorHtmlBuilderTagResolver("admin_user_metadata_table") {
            table("nice-table nice-table-striped nice-table-hover") {
                id = "admin-user-metadata-table"
                metadataRow("user id", data.userId)
                metadataRow("username", data.username)
                metadataRow("email", data.email ?: "n/a")
                metadataRow("user type", data.userType?.name ?: "n/a")
                metadataRow("country", data.country ?: "n/a")
                metadataRow("created", formatTimestamp(data.creationTime))
                metadataRow("last online", formatTimestamp(data.lastOnlineTime))
                metadataRow("last profile update", formatTimestamp(data.lastProfileUpdateTime))
                metadataRow("email confirmed", formatTimestamp(data.emailConfirmedAtTime))
                metadataRow("role admin", data.hasRoleAdmin.toString())
                metadataRow("role editor", data.hasRoleEditor.toString())
            }
        }

    private fun gameActivityTableTagResolver(data: AdminUserInsightsResponse) =
        KtorHtmlBuilderTagResolver("admin_user_game_activity_table") {
            table("nice-table nice-table-striped nice-table-hover") {
                id = "admin-user-game-activity-table"
                tr {
                    th { +"mode" }
                    th { +"first played" }
                    th { +"last played" }
                    th { +"total played" }
                }
                activityRow("puzzles", data.puzzle)
                activityRow("pvp", data.pvp)
                activityRow("pvb", data.pvb)
            }
        }

    private fun latestPageViewsTagResolver(data: AdminUserInsightsResponse) =
        KtorHtmlBuilderTagResolver("admin_user_latest_page_views_table") {
            if (data.latestPageViews.isEmpty()) {
                p("empty-block-placeholder") { +"No page views found." }
            } else {
                table("nice-table nice-table-striped nice-table-hover") {
                    id = "admin-user-latest-page-views-table"
                    tr {
                        th { +"url" }
                        th { +"time" }
                    }
                    data.latestPageViews.forEach { entry ->
                        tr {
                            td { +entry.url }
                            td { +formatTimestamp(entry.time) }
                        }
                    }
                }
            }
        }

    private fun latestSessionsTagResolver(data: AdminUserInsightsResponse) =
        KtorHtmlBuilderTagResolver("admin_user_latest_sessions_table") {
            if (data.latestSessions.isEmpty()) {
                p("empty-block-placeholder") { +"No sessions found." }
            } else {
                table("nice-table nice-table-striped nice-table-hover") {
                    id = "admin-user-latest-sessions-table"
                    tr {
                        th { +"os" }
                        th { +"agent" }
                        th { +"country" }
                        th { +"region" }
                        th { +"city" }
                        th { +"IP" }
                        th { +"created" }
                        th { +"updated" }
                    }
                    data.latestSessions.forEach { entry ->
                        tr {
                            td { +entry.os }
                            td { +entry.agentName }
                            td { +(entry.countryName ?: entry.countryCode ?: "n/a") }
                            td { +(entry.region ?: "n/a") }
                            td { +(entry.city ?: "n/a") }
                            td { +entry.remoteAddress }
                            td { +formatTimestamp(entry.created) }
                            td { +formatTimestamp(entry.updated) }
                        }
                    }
                }
            }
        }

    private fun kotlinx.html.TABLE.metadataRow(label: String, value: String) {
        tr {
            th { +label }
            td { +value }
        }
    }

    private fun kotlinx.html.TABLE.activityRow(label: String, summary: AdminUserInsightsResponse.ActivitySummary) {
        tr {
            td { +label }
            td { +formatTimestamp(summary.firstPlayed) }
            td { +formatTimestamp(summary.lastPlayed) }
            td { +summary.totalPlayed.toString() }
        }
    }

    private fun formatTimestamp(epochMillis: Long?): String =
        if (epochMillis == null) {
            "n/a"
        } else {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                Instant
                    .ofEpochMilli(epochMillis)
                    .atZone(UTC)
                    .toLocalDateTime(),
            ) + " UTC"
        }
}
