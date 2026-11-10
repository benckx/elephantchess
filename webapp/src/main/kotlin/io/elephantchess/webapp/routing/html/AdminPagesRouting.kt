package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.renderTemplateHtmlToCall
import io.elephantchess.webapp.ops.requireAdminRole
import io.elephantchess.webapp.rendering.AdminUserInsightsPageRenderer
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.routing.*

private val logger = logger {}

private val adminPagesMapping = mapOf(
    "/admin" to "admin/admin_overview",
    "/admin/feeds" to "admin/admin_feeds",
    "/admin/sock-puppet-report" to "admin/admin_sock_puppet_report",
    "/admin/variant-feeds" to "admin/admin_variant_feeds",
    "/admin/elo-stats" to "admin/admin_user_elo_stats",
    "/admin/content-section-feedback" to "admin/admin_content_section_feedback",
    "/admin/sessions" to "admin/admin_user_sessions",
    "/admin/analytics" to "admin/admin_analytics",
    "/admin/analytics-ads" to "admin/admin_analytics_ads",
    "/admin/monthly-metrics" to "admin/admin_monthly_metrics",
    "/admin/matching-time" to "admin/admin_matching_time",
    "/admin/online-users-stats" to "admin/admin_online_users_stats",
    "/admin/monthly-page-views" to "admin/admin_monthly_page_views",
    "/admin/daily-page-views" to "admin/admin_daily_page_views",
    "/admin/chat-messages" to "admin/admin_chat_messages",
    "/admin/pre-analysis" to "admin/admin_pre_analysis",
    "/admin/move-annotation-failures" to "admin/admin_move_annotation_failures",
    "/admin/users" to "admin/admin_users",
    "/admin/db-search-queries" to "admin/admin_db_search_queries",
    "/admin/password-recovery-attempts" to "admin/admin_password_recovery_attempts",
    "/admin/database-table-sizes" to "admin/admin_database_table_sizes",
    "/admin/setting-preferences" to "admin/admin_setting_preferences",
    "/admin/thrown-exceptions" to "admin/admin_thrown_exceptions",
    "/admin/newsletter-stats" to "admin/admin_newsletter_stats",
    "/admin/email-bounces" to "admin/admin_email_bounces",
    "/admin/player-profile-edits" to "admin/admin_player_profile_edits",
    "/admin/upcoming-events" to "admin/admin_upcoming_events",
)

internal fun Route.adminPagesRouting() {
    val simplePageRenderer by koin<SimplePageRenderer>()
    val adminUserInsightsRenderer by koin<AdminUserInsightsPageRenderer>()

    adminPagesMapping.forEach { (path, templateName) ->
        logger.info { "[admin] mapping html file $templateName to path $path" }
        get(path) {
            requireAdminRole {
                simplePageRenderer.renderTemplateHtmlToCall(templateName)
            }
        }
    }
    get("/admin/user/{identifier}") {
        requireAdminRole { _ ->
            val identifier = call.parameters["identifier"]
                ?: throw BadRequestException("identifier not provided")

            TextContent(
                text = adminUserInsightsRenderer.renderUserInsightsPage(identifier),
                contentType = ContentType.Text.Html,
            )
        }
    }
    get("/admin/user/{identifier}/page-views") {
        requireAdminRole { _ ->
            val identifier = call.parameters["identifier"]
                ?: throw BadRequestException("identifier not provided")

            TextContent(
                text = adminUserInsightsRenderer.renderUserPageViewsPage(identifier),
                contentType = ContentType.Text.Html,
            )
        }
    }
    get("/admin/user/{identifier}/chat-messages") {
        requireAdminRole { _ ->
            val identifier = call.parameters["identifier"]
                ?: throw BadRequestException("identifier not provided")

            TextContent(
                text = adminUserInsightsRenderer.renderUserChatMessagesPage(identifier),
                contentType = ContentType.Text.Html,
            )
        }
    }
}
