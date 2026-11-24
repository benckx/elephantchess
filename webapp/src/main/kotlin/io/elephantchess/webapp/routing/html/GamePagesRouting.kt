package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.respondBadRequestPage
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.GamePageRenderer
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.gamePagesRouting() {
    val gamePageRenderer by koin<GamePageRenderer>()

    get("/game") {
        val gameId = call.parameters["id"]
        if (gameId == null) {
            call.respondBadRequestPage()
        } else {
            call.respondHtml(gamePageRenderer.renderPvpGamePage(gameId))
        }
    }

    get("/playbot") {
        val gameId = call.parameters["id"]
        if (gameId == null) {
            call.respondBadRequestPage()
        } else {
            call.respondHtml(gamePageRenderer.renderPvbGamePage(gameId))
        }
    }
}
