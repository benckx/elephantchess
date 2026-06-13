package io.elephantchess.webapp.routing.api

import io.elephantchess.servicelayer.dto.ws.LiveGamesSubscription
import io.elephantchess.servicelayer.services.LobbyService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

private val lobbyService by koin<LobbyService>()

fun Route.lobbyRoutes() {
    route("/api/lobby") {
        get("/upcoming-events") {
            call.respond(lobbyService.listUpcomingEvents())
        }
    }
}

fun Route.lobbyWsRoutes() {
    webSocket("ws/lobby/live-games") {
        var sessionId: String? = null

        handleBidirectionalWebSocketSession<LiveGamesSubscription>(
            { lobbyService.startLiveGamesSession { update -> sendWs(update) }.also { sessionId = it } },
            { subscription -> sessionId?.let { lobbyService.handleLiveGamesSubscription(it, subscription.gameIds) } },
            { lobbyService.closeLiveGamesSession(it) }
        )
    }
}
