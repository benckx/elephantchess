package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.BoardGuiExampleRenderer
import io.ktor.server.routing.*

fun Routing.boardGuiExample() {
    val renderer by koin<BoardGuiExampleRenderer>()

    get("/about/developers/board-gui-example") {
        val useCdn = call.request.queryParameters["source"]?.lowercase() != "internal"
        val templateName = "about/developers/board-gui-example"
        call.respondHtml(
            renderer.renderBoardGuiExample(useCdn, templateName)
        )
    }
}
