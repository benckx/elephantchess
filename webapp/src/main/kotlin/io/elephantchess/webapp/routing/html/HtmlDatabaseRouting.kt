package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.dto.database.DatabasePlayer
import io.elephantchess.servicelayer.dto.database.DatabasePlayerProfileEdit
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.services.DatabaseService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.renderTemplateHtml
import io.elephantchess.webapp.ops.requireEditorRole
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.DatabasePageRenderer
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val databaseService by koin<DatabaseService>()

internal fun Routing.databasePages() {
    val databasePageRenderer by koin<DatabasePageRenderer>()
    val roleErrorPageRender: (suspend () -> String) =
        { simplePageRenderer.renderTemplate("database/database_player_edit_unauthorized") }

    get("/userdata/db-edits") {
        requireEditorRole(roleErrorPageRender) { _ ->
            simplePageRenderer.renderTemplateHtml("userdata/my_db_edits")
        }
    }
    get("/database/player/{playerName}") {
        resolveDatabasePlayer { databasePlayer ->
            val version = call.request.queryParameters["version"]?.toIntOrNull()
            val fetchEditorsUsername: suspend () -> List<String> = {
                databaseService.listAllEditorsUsername(databasePlayer.id)
            }

            databasePageRenderer.renderPlayerPage(
                databasePlayer = databasePlayer,
                requestedVersion = version,
                edit = databaseService.fetchPlayerEdit(databasePlayer.id, version),
                fetchEditorsUsername = fetchEditorsUsername
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

            val fetchEdit: suspend (Int) -> DatabasePlayerProfileEdit = { version ->
                databaseService.fetchPlayerEdit(databasePlayer.id, version)
            }

            // TODO: if version doesn't exist (too high), throw error
            databasePageRenderer.renderPlayerPageDiff(
                databasePlayer = databasePlayer,
                fromVersion = version - 1,
                toVersion = version,
                fetchEdit = fetchEdit
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

        val event = databaseService.fetchEvent(eventId)
        call.respondHtml(databasePageRenderer.renderEventPage(event))
    }
    get("/database/events") {
        // list of events page
        val events = databaseService.listAllEventsWithStats(limit = 250)
        call.respondHtml(databasePageRenderer.renderEventsListPage(events))
    }
    get("/database/players") {
        // list of players (W, L, D, games played) page
        val players = databaseService.listAllPlayersWithStats(limit = 250)
        call.respondHtml(databasePageRenderer.renderPlayersListPage(players))
    }
    get("/browse/event") {
        // browse games for an event
        val eventId = call.parameters["id"]
            ?: throw BadRequestException("id query parameter not provided")

        val round = call.request.queryParameters["round"]?.toIntOrNull()

        val eventName = databaseService.fetchEventName(eventId)
            ?: throw NotFoundException("Event not found")

        call.respondHtml(databasePageRenderer.renderBrowseEventGamesPage(eventId, eventName, round))
    }
    get("/database/game") {
        val gameId = call.parameters["id"]
            ?: throw BadRequestException("id query parameter not provided")

        val summary = databaseService.fetchGameSummary(gameId)
            ?: throw NotFoundException("Game not found")

        call.respondHtml(databasePageRenderer.renderGamePage(summary))
    }
}

private suspend fun RoutingContext.resolveDatabasePlayer(render: suspend (DatabasePlayer) -> String) {
    val playerName = call.parameters["playerName"]?.replace("_", " ")
        ?: throw BadRequestException("playerName not provided")

    val databasePlayer = databaseService.resolvePlayerByName(playerName)
    if (databasePlayer == null) {
        // player not found, render 404 page
        call.respondHtml(simplePageRenderer.renderTemplate("404"))
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
