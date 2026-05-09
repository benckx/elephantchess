package io.elephantchess.webapp.routing.api

import io.elephantchess.servicelayer.services.GameDataService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.requireGlobalGameId
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.gameDataRoutes() {
    val gameDataService by koin<GameDataService>()

    route("/api/game-data") {
        get("/game-metadata") {
            requireGlobalGameId { gameId ->
                gameDataService.fetchGameMetadata(gameId)
            }
        }
        get("/game-moves") {
            requireGlobalGameId { gameId ->
                gameDataService.fetchMoves(gameId)
            }
        }
        get("/start-game-analysis") {
            requireGlobalGameId { gameId ->
                gameDataService.startGameAnalysis(gameId)
            }
        }
        get("/game-analysis-status") {
            requireGlobalGameId { gameId ->
                gameDataService.fetchAnalysisStatus(gameId)
            }
        }
        get("/game-analysis-data") {
            requireGlobalGameId { gameId ->
                gameDataService.fetchAnalysisData(gameId)
            }
        }
        get("/list-latest-pvp-games") {
            paginationParams { limit, continuation, distinctByUsers ->
                gameDataService.listLastPvpGames(
                    requestedLimit = limit,
                    distinctByUsers = distinctByUsers,
                    beforeTs = continuation
                )
            }
        }
        get("/list-latest-pvb-games") {
            paginationParams { limit, continuation, distinctByUsers ->
                gameDataService.listLastPvbGames(
                    requestedLimit = limit,
                    distinctByUsers = distinctByUsers,
                    beforeTs = continuation
                )
            }
        }
        get("/list-db-player-games") {
            paginationParams { limit, continuation, _ ->
                val playerName = call.parameters["playerName"]?.replace("_", " ")
                    ?: throw BadRequestException("playerName parameter is required")

                gameDataService.listLastDbGamesByPlayerName(
                    requestedLimit = limit,
                    canonicalPlayerName = playerName,
                    offset = continuation?.toInt()
                )
            }
        }
        get("/list-db-event-games") {
            paginationParams { limit, continuation, _ ->
                val eventId = call.parameters["eventId"]
                    ?: throw BadRequestException("eventId parameter is required")
                val round = call.parameters["round"]?.toIntOrNull()

                gameDataService.listLastDbGamesByEventId(
                    requestedLimit = limit,
                    eventId = eventId,
                    round = round,
                    offset = continuation?.toInt()
                )
            }
        }
    }
}

suspend fun RoutingContext.paginationParams(
    handler: suspend (Int, Long?, Boolean) -> Any,
) {
    val limit = (call.parameters["limit"]?.toInt() ?: 18)
        .coerceAtLeast(1)
        .coerceAtMost(24)

    val continuation = call.parameters["continuation"]?.toLong()
    val distinctByUsers = (call.parameters["distinctByUsers"] ?: "true").toBoolean()

    call.respond(handler(limit, continuation, distinctByUsers))
}
