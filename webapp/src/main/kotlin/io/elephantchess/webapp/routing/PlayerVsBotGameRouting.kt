package io.elephantchess.webapp.routing

import io.elephantchess.servicelayer.dto.botgame.CancelBotGameRequest
import io.elephantchess.servicelayer.dto.botgame.CreateBotGameRequest
import io.elephantchess.servicelayer.dto.botgame.PlayMoveBotGameRequest
import io.elephantchess.servicelayer.dto.botgame.ResignBotGameRequest
import io.elephantchess.servicelayer.services.PlayerVsBotGameService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

private val pvbGameService by koin<PlayerVsBotGameService>()

fun Route.botGameRoutes() {
    route("/api/botgame") {
        get("/data") {
            requireGameId { gameId ->
                pvbGameService.fetchGameData(gameId)
            }
        }
        get("/moves-history") {
            // TODO: use generic service instead
            requireGameId { gameId ->
                pvbGameService.fetchMoveHistory(gameId)
            }
        }
        post("/create") {
            requireIdentificationWithBody<CreateBotGameRequest> { verifiedToken, request ->
                pvbGameService.create(verifiedToken.userId(), request)
            }
        }
        post("/play-move") {
            requireIdentificationWithBody<PlayMoveBotGameRequest> { verifiedToken, request ->
                pvbGameService.playMove(verifiedToken.userId, request)
            }
        }
        post("/resign") {
            requireIdentificationWithBody<ResignBotGameRequest> { verifiedToken, request ->
                pvbGameService.resign(verifiedToken.userId, request)
            }
        }
        post("/cancel") {
            requireIdentificationWithBody<CancelBotGameRequest> { verifiedToken, request ->
                pvbGameService.cancel(verifiedToken.userId, request)
            }
        }
        get("/list-user-games") {
            val continuation = call.parameters["continuation"]?.toLong()
            requireIdentification { verifiedToken ->
                pvbGameService.listUserGames(verifiedToken.userId, continuation)
            }
        }
    }
}

fun Route.botGameWsRoutes() {
    webSocket("ws/botgame/watch-as-spectator") {
        val gameId = call.parameters["gameId"]
            ?: throw IllegalArgumentException("gameId is required")
        val moveIndex = call.parameters["moveIndex"]?.toIntOrNull()
            ?: throw IllegalArgumentException("moveIndex is required")

        handleWebSocketSession(
            { pvbGameService.startSpectatorSession(gameId, moveIndex) { update -> sendWs(update) } },
            { sessionId -> pvbGameService.closeSpectatorSession(sessionId) }
        )
    }
}
