package io.elephantchess.webapp.routing.html

import io.elephantchess.config.AppConfig
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.services.PuzzleCache
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.puzzlesPageRouting() {
    val appConfig by koin<AppConfig>()
    val puzzleCache by koin<PuzzleCache>()
    val isPuzzlesDebugModeEnabled = appConfig.isPuzzlesDebugModeEnabled
    val resolvers = listOf(SimpleValueTagResolver("puzzles_debug_mode", isPuzzlesDebugModeEnabled.toString()))
    val simplePageRenderer by koin<SimplePageRenderer>()

    get("/puzzles") {
        val id = call.parameters["id"]

        if (id != null && !puzzleCache.exists(id)) {
            throw NotFoundException("Puzzle $id not found")
        }

        call.respondHtml(
            simplePageRenderer.renderTemplate("puzzles", resolvers),
        )
    }
}
