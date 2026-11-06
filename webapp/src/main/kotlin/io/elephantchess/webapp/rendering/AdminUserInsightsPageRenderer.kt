package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.KtorHtmlBuilderTagResolver
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsMetadata
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
        val metadata = adminUserInsightsService.fetchUserMetadata(identifier)
        val encodedIdentifier = identifier.encodeURLPath()

        return htmlRenderer.renderHtml(
            templatePath = "/templates/admin/admin_user_insights.html",
            canonicalPath = "/admin/user/$encodedIdentifier",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("username", metadata.username),
                SimpleValueTagResolver("identifier", identifier),
                publicProfileLinkTagResolver(metadata),
                userMetadataTableTagResolver(metadata),
            ),
        )
    }

    suspend fun renderUserPageViewsPage(identifier: String): String {
        val metadata = adminUserInsightsService.fetchUserMetadata(identifier)
        val encodedIdentifier = identifier.encodeURLPath()

        return htmlRenderer.renderHtml(
            templatePath = "/templates/admin/admin_user_insights_page_views.html",
            canonicalPath = "/admin/user/$encodedIdentifier/page-views",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("username", metadata.username),
                SimpleValueTagResolver("identifier", identifier),
                SimpleValueTagResolver("encoded_identifier", encodedIdentifier),
            ),
        )
    }

    suspend fun renderUserChatMessagesPage(identifier: String): String {
        val metadata = adminUserInsightsService.fetchUserMetadata(identifier)
        val encodedIdentifier = identifier.encodeURLPath()

        return htmlRenderer.renderHtml(
            templatePath = "/templates/admin/admin_user_insights_chat_messages.html",
            canonicalPath = "/admin/user/$encodedIdentifier/chat-messages",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("username", metadata.username),
                SimpleValueTagResolver("identifier", identifier),
                SimpleValueTagResolver("encoded_identifier", encodedIdentifier),
            ),
        )
    }

    private fun publicProfileLinkTagResolver(data: AdminUserInsightsMetadata) =
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

    private fun userMetadataTableTagResolver(data: AdminUserInsightsMetadata) =
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

    private fun TABLE.metadataRow(label: String, value: String) {
        tr {
            th { +label }
            td { greyOutText(value) }
        }
    }

    private fun TD.greyOutText(value: String) {
        if (value == "n/a") {
            span("na-value") { +value }
        } else {
            +value
        }
    }

    private fun formatEmailValidityStatus(data: AdminUserInsightsMetadata): String {
        val status = data.emailValidityStatus ?: return "n/a"
        return status
            .name
            .lowercase()
            .replace('_', ' ')
    }

    private fun formatTimestampWithRelativeAge(epochMillis: Long?): String {
        val formattedTimestamp = formatTimestampUtc(epochMillis)
        val relative = formatTimestampToRelativeTime(epochMillis)
        return if (epochMillis == null) formattedTimestamp else "$formattedTimestamp ($relative)"
    }
}
