package io.elephantchess.webapp.routing.html

import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.ModalRenderer
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
        modals()
        gamePagesRouting()
        puzzlesPageRouting()
        userBasedPagesRouting()
        databasePagesRouting()
        aboutPagesRouting()
        boardGuiDocumentationPageRouting()
        adminPagesRouting()
        emailSettingUpdatePages()
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
