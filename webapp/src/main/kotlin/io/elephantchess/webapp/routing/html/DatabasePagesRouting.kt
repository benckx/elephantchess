package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.dto.database.DatabasePlayer
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.services.DatabaseService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.renderTemplateHtmlToCall
import io.elephantchess.webapp.ops.requireEditorRole
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.ops.respondNotFoundPage
import io.elephantchess.webapp.rendering.DatabasePageRenderer
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val databaseService by koin<DatabaseService>()

internal fun Routing.databasePagesRouting() {
    val databasePageRenderer by koin<DatabasePageRenderer>()
    val simplePageRenderer by koin<SimplePageRenderer>()

    val roleErrorPageRender: (suspend () -> String) =
        { simplePageRenderer.renderTemplate("database/database_player_edit_unauthorized") }

    get("/userdata/db-edits") {
        requireEditorRole(roleErrorPageRender) { _ ->
            simplePageRenderer.renderTemplateHtmlToCall("userdata/my_db_edits")
        }
    }
    get("/database/player/{playerName}") {
        resolveDatabasePlayer { databasePlayer ->
            val version = call.request.queryParameters["version"]?.toIntOrNull()
            databasePageRenderer.renderPlayerPage(
                databasePlayer = databasePlayer,
                requestedVersion = version,
            )
        }
    }
    get("/database/player/{playerName}/edit") {
        requireEditorRole(roleErrorPageRender) { _ ->
            resolveDatabasePlayer { databasePlayer ->
                databasePageRenderer.renderPlayerEditPage(databasePlayer)
            }
        }
    }
    get("/database/player/{playerName}/edit-history") {
        resolveDatabasePlayer { databasePlayer ->
            databasePageRenderer.renderEditHistoryPage(databasePlayer)
        }
    }
    get("/database/player/{playerName}/edit-diff") {
        resolveDatabasePlayer { databasePlayer ->
            val version = call.request.queryParameters["version"]?.toIntOrNull()
                ?: throw BadRequestException("version query parameters must be provided")

            if (version < 1) {
                throw BadRequestException("version must be >= 1 to show a diff")
            }

            // TODO: if version doesn't exist (too high), throw error
            databasePageRenderer.renderPlayerPageDiff(
                databasePlayer = databasePlayer,
                version = version,
            )
        }
    }
    get("/database/player/{playerName}/games") {
        // browse games for a player
        resolveDatabasePlayer { databasePlayer ->
            databasePageRenderer.renderBrowseGamesPage(databasePlayer)
        }
    }
    get("/database/event") {
        // single event page
        val eventId = call.parameters["id"]
            ?: throw BadRequestException("id query parameter not provided")

        call.respondHtml(databasePageRenderer.renderEventPage(eventId))
    }
    get("/database/events") {
        // list of events page
        call.respondHtml(databasePageRenderer.renderEventsListPage())
    }
    get("/database/players") {
        // list of players (W, L, D, games played) page
        call.respondHtml(databasePageRenderer.renderPlayersListPage())
    }
    get("/browse/event") {
        // browse games for an event
        val eventId = call.parameters["id"]
            ?: throw BadRequestException("id query parameter not provided")

        val round = call.request.queryParameters["round"]?.toIntOrNull()
        call.respondHtml(databasePageRenderer.renderBrowseEventGamesPage(eventId, round))
    }
    get("/database/game") {
        val gameId = call.parameters["id"] ?: throw BadRequestException("id query parameter not provided")
        val orientation = call.request.queryParameters["orientation"]
        call.respondHtml(databasePageRenderer.renderGamePage(gameId, orientation))
    }
}

private suspend fun RoutingContext.resolveDatabasePlayer(render: suspend (DatabasePlayer) -> String) {
    val playerName = call.parameters["playerName"]?.replace("_", " ")
        ?: throw BadRequestException("playerName not provided")

    val databasePlayer = databaseService.resolvePlayerByName(playerName)
    if (databasePlayer == null) {
        // player not found, render 404 page
        call.respondNotFoundPage()
    } else if (databasePlayer.urlName != playerName.replace(" ", "_")) {
        // reconstruct the correct URL path by replacing the playerName segment
        val originalPath = call.request.path()
        val pathSegments = originalPath.split("/")
        val playerNameIndex = pathSegments.indexOfFirst { it.decodeURLPart() == playerName.replace(" ", "_") }

        val correctUrl = if (playerNameIndex >= 0) {
            // rebuild path with correct player name
            pathSegments.toMutableList()
                .apply { this[playerNameIndex] = databasePlayer.urlName.encodeURLPath() }
                .joinToString("/")
        } else {
            // fallback: just reconstruct from scratch using the route pattern
            originalPath.substringBefore("/database/player/") +
                "/database/player/${databasePlayer.urlName.encodeURLPath()}" +
                originalPath
                    .substringAfter("/database/player/")
                    .substringAfter("/").let { if (it.isEmpty()) "" else "/$it" }
        }

        val queryString = call.request.queryString()
        val correctUrlWithQuery = if (queryString.isNotEmpty()) "$correctUrl?$queryString" else correctUrl
        call.respondRedirect(correctUrlWithQuery)
    } else {
        // canonical name, render the player page
        call.respondHtml(render(databasePlayer))
    }
}
