package io.elephantchess.webapp.routing

import io.elephantchess.servicelayer.services.SevenKingdomsGameService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.requireGameId
import io.ktor.server.routing.*

private val gameService by koin<SevenKingdomsGameService>()

fun Route.sevenKingdomsGameRoutes() {
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
