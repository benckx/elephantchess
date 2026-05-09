package io.elephantchess.webapp.routing.api

import io.elephantchess.servicelayer.services.SevenKingdomsGameService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.requireGameId
import io.ktor.server.routing.*

fun Route.sevenKingdomsGameRoutes() {
    val gameService by koin<SevenKingdomsGameService>()

    route("/api/7k") {
        get("/fetch-game-data") {
            requireGameId { gameId ->
                gameService.fetchGameData(gameId)
            }
        }
        get("/fetch-moves") {
            requireGameId { gameId ->
                gameService.fetchMoves(gameId)
            }
        }
    }
}
