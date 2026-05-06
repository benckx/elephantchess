package io.elephantchess.webapp.ops

import io.elephantchess.model.GameId
import io.elephantchess.model.GameType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.response.*
import io.ktor.server.routing.*

suspend fun RoutingContext.requireParam(
    paramName: String,
    handler: suspend (String) -> Any,
) {
    val paramValue = call.parameters[paramName]
    if (paramValue == null) {
        call.response.status(BadRequest)
    } else {
        call.respond(handler(paramValue))
    }
}

suspend fun RoutingContext.requireGameId(handler: suspend (String) -> Any) {
    requireParam("gameId", handler)
}

suspend fun RoutingContext.requireUserId(handler: suspend (String) -> Any) {
    requireParam("userId", handler)
}

suspend fun RoutingContext.requireGlobalGameId(
    handler: suspend (GameId) -> Any,
) {
    val gameIdParam = call.parameters["gameId"]
    val gameTypeParam = call.parameters["gameType"]
    val gameTypeValues = GameType.entries.map { it.name }

    if (gameIdParam == null) {
        call.respondText { "gameId is required" }
        call.response.status(BadRequest)
    } else if (gameTypeParam == null) {
        call.respondText { "gameType is required" }
        call.response.status(BadRequest)
    } else if (!gameTypeValues.contains(gameTypeParam)) {
        call.respondText { "gameType must be one of ${gameTypeValues.joinToString(", ")}" }
        call.response.status(BadRequest)
    } else {
        val gameId = GameId(GameType.valueOf(gameTypeParam), gameIdParam)
        call.respond(handler(gameId))
    }
}
