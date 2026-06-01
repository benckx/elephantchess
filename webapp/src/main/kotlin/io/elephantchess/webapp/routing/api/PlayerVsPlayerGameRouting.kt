package io.elephantchess.webapp.routing.api

import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.game.*
import io.elephantchess.servicelayer.dto.ws.PlayerVsPlayerInput
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.model.VerifiedToken
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.elephantchess.servicelayer.services.TokenManager
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

private val pvpGameService by koin<PlayerVsPlayerGameService>()
private val tokenManager by koin<TokenManager>()
private val logger = KotlinLogging.logger {}

fun Route.pvpGameRoutes() {
    route("/api/game") {
        post("/create") {
            requireIdentificationWithBody<CreateGameRequest> { verifiedToken, request ->
                pvpGameService.createGame(verifiedToken.userId(), request)
            }
        }
        get("/list-user-games") {
            val continuation = call.parameters["continuation"]?.toLong()
            requireIdentification { verifiedToken ->
                pvpGameService.listUserGames(verifiedToken.userId, continuation)
            }
        }
        get("/data") {
            requireGameId { gameId ->
                pvpGameService.fetchGame(gameId)
            }
        }
        get("/moves-history") {
            requireGameId { gameId ->
                pvpGameService.fetchMoveHistory(gameId)
            }
        }
        get("/chat-history") {
            requireGameId { gameId ->
                pvpGameService.fetchChatHistory(gameId)
            }
        }
        post("/join") {
            requireIdentificationWithBody<JoinGameRequest> { verifiedToken, request ->
                pvpGameService.joinGame(verifiedToken.userId(), request)
            }
        }
        post("/cancel") {
            requireIdentificationWithBody<CancelGameRequest> { verifiedToken, request ->
                pvpGameService.cancel(verifiedToken.userId, request)
            }
        }
        post("/resign") {
            requireIdentificationWithBody<ResignGameRequest> { verifiedToken, request ->
                pvpGameService.resign(verifiedToken.userId, request)
            }
        }
        post("/propose-draw") {
            requireIdentificationWithBody<ProposeDrawRequest> { verifiedToken, request ->
                pvpGameService.proposeDraw(verifiedToken.userId, request)
            }
        }
        post("/respond-to-draw") {
            requireIdentificationWithBody<RespondToDrawRequest> { verifiedToken, request ->
                pvpGameService.respondToDraw(verifiedToken.userId, request)
            }
        }
        post("/play-move") {
            requireIdentificationWithBody<PlayMoveRequest> { verifiedToken, request ->
                pvpGameService.playMove(verifiedToken.userId, request)
            }
        }
    }
}

fun Route.pvpGameWsRoutes() {
    webSocket("ws/pvp/games-to-play") {
        val userId = call.parameters["userId"]
            ?: throw IllegalArgumentException("userId is required")
        val userTypeStr = call.parameters["userType"]
            ?: throw IllegalArgumentException("userType is required")
        val userType = UserType.valueOf(userTypeStr)

        handleWebSocketSession(
            { pvpGameService.startGamesToPlaySession(UserId(userType, userId)) { sendWs(it) } },
            { pvpGameService.closeGamesToPlaySession(it) }
        )
    }
    webSocket("ws/pvp/game") {
        val token = call.parameters["token"]
            ?: throw IllegalArgumentException("token is required")
        val gameId = call.parameters["gameId"]
            ?: throw IllegalArgumentException("gameId is required")

        val userId = when (val result = tokenManager.verifyToken(token)) {
            is VerifiedToken -> result.userId()
            else -> {
                // An invalid/expired token on a WebSocket is normal client behaviour (e.g. a stale
                // tab reconnecting after a key rotation or after the guest token TTL expired).
                // Close the socket cleanly instead of throwing, which would otherwise bubble up
                // uncaught and be logged as an ERROR ("Websocket handler failed") with a full stack trace.
                logger.debug { "rejecting PvP game WebSocket for game $gameId: $result" }
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }
        }

        handleBidirectionalWebSocketSession<PlayerVsPlayerInput>(
            { pvpGameService.startPlayerVsPlayerSession(userId, gameId) { sendWs(it) } },
            { message -> pvpGameService.handlePlayerVsPlayerInput(userId, gameId, message) },
            { pvpGameService.closePlayerVsPlayerSession(it) }
        )
    }
}
