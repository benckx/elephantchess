package io.elephantchess.webapp.routing.api

import io.elephantchess.servicelayer.dto.lobby.AlwaysVisibleInLobbyAllowedResponse
import io.elephantchess.servicelayer.dto.lobby.LatestGamesUpdateRequest
import io.elephantchess.servicelayer.services.GameDataService
import io.elephantchess.servicelayer.services.LobbyService
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.requireIdentification
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.lobbyRoutes() {
    val lobbyService by koin<LobbyService>()
    val gameDataService by koin<GameDataService>()
    val pvpGameService by koin<PlayerVsPlayerGameService>()

    route("/api/lobby") {
        get("/upcoming-events") {
            call.respond(lobbyService.listUpcomingEvents())
        }
        post("/latest-games-update") {
            val request = call.receive<LatestGamesUpdateRequest>()
            call.respond(gameDataService.fetchLatestGamesUpdate(request))
        }
        get("/always-visible-in-lobby-allowed") {
            requireIdentification { verifiedToken ->
                AlwaysVisibleInLobbyAllowedResponse(
                    allowed = pvpGameService.isAlwaysVisibleInLobbyAllowed(verifiedToken.userId)
                )
            }
        }
    }
}
