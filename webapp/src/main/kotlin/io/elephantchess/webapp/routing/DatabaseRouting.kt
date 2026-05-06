package io.elephantchess.webapp.routing

import io.elephantchess.servicelayer.dto.database.DatabasePlayerUpdateRequest
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.services.DatabaseService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.handleValidatedResponse
import io.elephantchess.webapp.ops.requireEditorRole
import io.elephantchess.webapp.ops.requireIdentification
import io.elephantchess.xiangqi.Color
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * What we call "database" in this context is a repository of games played in tournaments,
 * and not specifically the SQL database.
 */
private const val MIN_LETTERS_TO_SEARCH = 2

fun Route.databaseRoutes() {
    val databaseService by koin<DatabaseService>()

    route("/api/database") {
        route("/autocomplete") {
            get("/players") {
                requireContains { contains -> databaseService.listPlayersSuggestions(contains) }
            }
            get("/events") {
                requireContains { contains -> databaseService.listEventsSuggestions(contains) }
            }
        }
        route("/info") {
            get("/count-all-games") {
                call.respond(databaseService.countAllGames())
            }
            get("/list-featured-players") {
                call.respond(databaseService.listRandomFeaturedPlayers())
            }
            get("/player/edit-history-by-player-id") {
                val playerId = call.parameters["playerId"]
                    ?: throw BadRequestException("playerName not provided")

                call.respond(databaseService.fetchPlayerEditHistory(playerId))
            }
            get("/player/find-possible-duplicates") {
                val playerId = call.parameters["playerId"]
                    ?: throw BadRequestException("playerId not provided")

                call.respond(databaseService.findPossibleDuplicatedPlayers(playerId))
            }
            get("/player/game-stats") {
                val playerId = call.parameters["playerId"]
                    ?: throw BadRequestException("playerId not provided")

                call.respond(databaseService.fetchPlayerGameStats(playerId))
            }
        }
        get("/list-user-edits") {
            requireEditorRole { verifiedToken ->
                call.respond(databaseService.findEditedPlayersLatestVersions(verifiedToken.userId))
            }
        }
        get("/player/current-edit") {
            val playerId = call.parameters["playerId"]
                ?: throw BadRequestException("playerId not provided")

            call.respond(databaseService.fetchPlayerEdit(playerId, null))
        }
        post("/player/edit") {
            requireEditorRole { verifiedToken ->
                handleValidatedResponse<DatabasePlayerUpdateRequest, Unit> { request ->
                    databaseService.updatePlayerProfile(
                        request = request,
                        userId = verifiedToken.userId
                    )
                }
            }
        }
        get("/search") {
            requireIdentification { verifiedToken ->
                val dateStart = call.request.queryParameters["dateStart"]
                val dateEnd = call.request.queryParameters["dateEnd"]
                val playerName = call.request.queryParameters["playerName"]
                val playerIds = call.request.queryParameters.getAll("playerIds") ?: emptyList()
                val playerColor = call.request.queryParameters["playerColor"]?.let {
                    try {
                        Color.valueOf(it.uppercase())
                    } catch (_: IllegalArgumentException) {
                        throw BadRequestException("Invalid playerColor: $it")
                    }
                }
                val eventName = call.request.queryParameters["eventName"]
                val eventIds = call.request.queryParameters.getAll("eventIds") ?: emptyList()
                val fen = call.request.queryParameters["fen"]
                val offset = call.request.queryParameters["continuation"]?.toIntOrNull()
                call.respond(
                    databaseService.search(
                        dateStart = dateStart,
                        dateEnd = dateEnd,
                        playerName = playerName,
                        playerIds = playerIds,
                        playerColor = playerColor,
                        eventName = eventName,
                        eventIds = eventIds,
                        fen = fen,
                        offset = offset,
                        userId = verifiedToken.userId,
                    )
                )
            }
        }
        get("/list-events") {
            val limit = call.parameters["limit"]?.toIntOrNull()?.coerceIn(1, 250) ?: 100
            val offset = call.parameters["offset"]?.toIntOrNull()
            call.respond(databaseService.listAllEventsWithStats(limit = limit, offset = offset))
        }
        get("/list-players") {
            val limit = call.parameters["limit"]?.toIntOrNull()?.coerceIn(1, 250) ?: 100
            val offset = call.parameters["offset"]?.toIntOrNull()
            call.respond(databaseService.listAllPlayersWithStats(limit = limit, offset = offset))
        }
    }
}

private suspend fun RoutingContext.requireContains(handler: suspend (String) -> Any) {
    val contains = call.parameters["contains"]
    if (contains == null || contains.length < MIN_LETTERS_TO_SEARCH) {
        call.response.status(BadRequest)
    } else {
        call.respond(handler(contains))
    }
}
