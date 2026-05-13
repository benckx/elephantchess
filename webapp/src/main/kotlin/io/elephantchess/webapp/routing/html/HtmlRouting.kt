package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.services.KofiService
import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.*
import io.elephantchess.webapp.rendering.ModalRenderer
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.elephantchess.webapp.rendering.UserProfilePageRenderer
import io.elephantchess.webapp.rendering.latestSupporterTagResolver
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val logger = logger {}

internal val simplePageRenderer by koin<SimplePageRenderer>()

private val publicPageMapping = mapOf(
    "/401" to "401",
    "/403" to "403",
    "/404" to "404",
    "/game" to "player_vs_player_game",
    "/puzzles" to "puzzles",
    "/board" to "simple_board",
    "/database" to "database/database_search",
    "/database/search" to "database/database_search",
    "/database/game" to "database/database_game_viewer",
    "/browse/player-vs-player" to "browse_pvp_games",
    "/browse/player-vs-bot" to "browse_pvb_games",
    "/analysis" to "analysis_board",
    "/global" to "global",
    "/recovery" to "password_recovery1",
    "/recovery/finalize" to "password_recovery2",
    "/about" to "about/about",
    "/about/changelog" to "about/changelog",
    "/about/faq" to "about/faq",
    "/contact" to "contact_form",
    "/7k/game" to "seven_kingdoms/seven_kingdoms_game",
    "/7k/playground" to "seven_kingdoms/seven_kingdoms_playground",
    "/7k/about" to "seven_kingdoms/seven_kingdoms_about",
)

private val publicPageMappingWithSupporterBanner = mapOf(
    "/" to "lobby",
    "/playbot" to "player_vs_bot",
)

private val publicPageRedirection = mapOf(
    "/7k" to "/7k/about",
    "/7k/rules" to "/7k/about",
)

// available to guests and authenticated users
private val identificationRequiredPagesMapping = mapOf(
    "/userdata/games" to "userdata/my_games",
    "/userdata/botgames" to "userdata/my_bot_games",
    "/userdata/analysis" to "userdata/my_analysis",
    "/userdata/puzzles" to "userdata/my_played_puzzles",
    "/userdata/db-searches" to "userdata/my_db_searches",
)

// only available authenticated users
private val authenticatedRequiredPagesMapping = mapOf(
    "/user/settings" to "user_settings",
)

private val adminPagesMapping = mapOf(
    "/admin" to "admin/admin_overview",
    "/admin/feeds" to "admin/admin_feeds",
    "/admin/sessions" to "admin/admin_user_sessions",
    "/admin/analytics" to "admin/admin_analytics",
    "/admin/analytics-ads" to "admin/admin_analytics_ads",
    "/admin/monthly-metrics" to "admin/admin_monthly_metrics",
    "/admin/online-users-stats" to "admin/admin_online_users_stats",
    "/admin/monthly-page-views" to "admin/admin_monthly_page_views",
    "/admin/daily-page-views" to "admin/admin_daily_page_views",
    "/admin/chat-messages" to "admin/admin_chat_messages",
    "/admin/pre-analysis" to "admin/admin_pre_analysis",
    "/admin/users" to "admin/admin_users",
    "/admin/db-search-queries" to "admin/admin_db_search_queries",
    "/admin/password-recovery-attempts" to "admin/admin_password_recovery_attempts",
    "/admin/database-table-sizes" to "admin/admin_database_table_sizes",
    "/admin/thrown-exceptions" to "admin/admin_thrown_exceptions",
    "/admin/newsletter-stats" to "admin/admin_newsletter_stats",
    "/admin/player-profile-edits" to "admin/admin_player_profile_edits",
    "/admin/upcoming-events" to "admin/admin_upcoming_events",
)

fun Application.htmlRoutingModule() {
    routing {
        simpleMappings()
        simpleMappingsWithSupporterBanner()
        boardGuiExample()
        userProfile()
        modals()
        databasePages()
    }
}

private fun Routing.simpleMappings() {
    publicPageMapping.forEach { (path, templateName) ->
        logger.info { "[public] mapping html file $templateName to path $path" }
        get(path) {
            call.respond(simplePageRenderer.renderTemplateHtml(templateName))
        }
    }
    publicPageRedirection.forEach { (fromPath, toPath) ->
        logger.info { "[public] mapping redirection from $fromPath to $toPath" }
        get(fromPath) {
            call.respondRedirect(toPath)
        }
    }
    identificationRequiredPagesMapping.forEach { (path, templateName) ->
        logger.info { "[identification] mapping html file $templateName to path $path" }
        get(path) {
            requireIdentification { _ ->
                simplePageRenderer.renderTemplateHtml(templateName)
            }
        }
    }
    authenticatedRequiredPagesMapping.forEach { (path, templateName) ->
        logger.info { "[authentication] mapping html file $templateName to path $path" }
        get(path) {
            requireAuthentication { _ ->
                simplePageRenderer.renderTemplateHtml(templateName)
            }
        }
    }
    adminPagesMapping.forEach { (path, templateName) ->
        logger.info { "[admin] mapping html file $templateName to path $path" }
        get(path) {
            requireAdminRole {
                simplePageRenderer.renderTemplateHtml(templateName)
            }
        }
    }
}

private fun Routing.simpleMappingsWithSupporterBanner() {
    val kofiService by koin<KofiService>()

    publicPageMappingWithSupporterBanner.forEach { (path, templateName) ->
        logger.info { "[public] mapping html file $templateName to path $path (with supporter banner)" }
        get(path) {
            val latestTipper = kofiService.fetchLatestSupporter()
            call.respondHtml(
                simplePageRenderer.renderTemplate(
                    templateName,
                    listOf(latestSupporterTagResolver(latestTipper))
                )
            )
        }
    }
}

private fun Route.userProfile() {
    val renderer by koin<UserProfilePageRenderer>()
    val userService by koin<UserService>()

    get("/@/{username}") {
        val username = call.parameters["username"]
            ?: throw BadRequestException("username not provided")

        val userProfileResponse = userService.fetchProfile(username)
        call.respondHtml(renderer.renderUserProfile(userProfileResponse))
    }
}

private fun Route.modals() {
    val renderer by koin<ModalRenderer>()

    get("/modal/{modalName}") {
        val modalName = call.parameters["modalName"]
        if (modalName == null) {
            call.respond(BadRequest)
        } else {
            val modalContent = renderer.renderModal(modalName)
            if (modalContent == null) {
                logger.error { "modal html file not found for '$modalName' " }
                call.respond(NotFound)
            } else {
                call.respondHtml(modalContent)
            }
        }
    }
}
