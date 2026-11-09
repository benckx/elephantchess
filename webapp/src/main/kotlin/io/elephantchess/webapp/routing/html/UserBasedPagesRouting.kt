package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.isAdmin
import io.elephantchess.webapp.ops.renderTemplateHtmlToCall
import io.elephantchess.webapp.ops.requireAuthentication
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.elephantchess.webapp.rendering.UserProfilePageRenderer
import io.elephantchess.webapp.rendering.UserSettingsPageRenderer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.routing.*

fun Route.userBasedPagesRouting() {
    userProfileAndBrowseGamesPages()
    userSettingsPages()
}

private fun Route.userProfileAndBrowseGamesPages() {
    val userProfilePageRenderer by koin<UserProfilePageRenderer>()
    val userService by koin<UserService>()

    get("/@/{username}") {
        val username = call.parameters["username"] ?: throw BadRequestException("username not provided")
        val isAdmin = isAdmin()

        val userProfileResponse = userService.fetchProfile(username)
        call.respondHtml(userProfilePageRenderer.renderUserProfile(userProfileResponse, isAdmin))
    }
    get("/@/{username}/browse-pvp-games") {
        val username = call.parameters["username"] ?: throw BadRequestException("username not provided")

        userService.validateUserExists(username)
        call.respondHtml(userProfilePageRenderer.renderUserBrowsePvpGames(username))
    }
    get("/@/{username}/browse-pvb-games") {
        val username = call.parameters["username"] ?: throw BadRequestException("username not provided")

        userService.validateUserExists(username)
        call.respondHtml(userProfilePageRenderer.renderUserBrowsePvbGames(username))
    }
}

private fun Route.userSettingsPages() {
    val userSettingsPageRenderer by koin<UserSettingsPageRenderer>()
    val simplePageRenderer by koin<SimplePageRenderer>()

    route("/user/settings") {
        get {
            requireAuthentication { token ->
                TextContent(
                    text = userSettingsPageRenderer.renderUserSettingsPage(token.userId),
                    contentType = ContentType.Text.Html,
                )
            }
        }
        get("/sessions") {
            requireAuthentication {
                simplePageRenderer.renderTemplateHtmlToCall("user_sessions")
            }
        }
        get("/views") {
            requireAuthentication {
                simplePageRenderer.renderTemplateHtmlToCall("user_views")
            }
        }
    }
}
