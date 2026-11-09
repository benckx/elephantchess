package io.elephantchess.webapp.routing.html

import io.elephantchess.config.AppConfig
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.services.PuzzleCache
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.respondBadRequestPage
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.*
import io.elephantchess.webapp.routing.emailSettingUpdatePages
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val logger = logger {}

fun Application.htmlRoutingModule() {
    routing {
        simpleMappings()
        gamePages()
        puzzlesPage()
        boardGuiExample()
        userProfileAndBrowseGamesPages()
        userSettingsPages()
        modals()
        databasePages()
        aboutPages()
        adminPagesRouting()
        emailSettingUpdatePages()
    }
}

private fun Route.gamePages() {
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

private fun Route.puzzlesPage() {
    val appConfig by koin<AppConfig>()
    val puzzleCache by koin<PuzzleCache>()
    val isPuzzlesDebugModeEnabled = appConfig.isPuzzlesDebugModeEnabled
    val resolvers = listOf(SimpleValueTagResolver("puzzles_debug_mode", isPuzzlesDebugModeEnabled.toString()))

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

private fun Route.aboutPages() {
    val changelogPageRenderer by koin<ChangelogPageRenderer>()
    val faqPageRenderer by koin<FaqPageRenderer>()
    val roadmapPageRenderer by koin<RoadmapPageRenderer>()

    get("/about/roadmap") {
        call.respondHtml(roadmapPageRenderer.renderRoadmapPage())
    }
    get("/about/faq") {
        call.respondHtml(faqPageRenderer.renderFaqPage())
    }
    get("/about/changelog") {
        call.respondHtml(changelogPageRenderer.renderChangelogPage())
    }
}

private fun Route.modals() {
    val renderer by koin<ModalRenderer>()

    get("/modal/{modalName}") {
        val modalName = call.parameters["modalName"]
        if (modalName == null) {
            call.respond(BadRequest)
        } else {
            val modalContent = renderer.renderModal(modalName)
            if (modalContent == null) {
                logger.error { "modal html file not found for '$modalName' " }
                call.respond(NotFound)
            } else {
                call.respondHtml(modalContent)
            }
        }
    }
}
