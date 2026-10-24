package io.elephantchess.webapp.ops

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*

private val simplePageRenderer by koin<SimplePageRenderer>()

suspend fun SimplePageRenderer.renderTemplateHtml(templateName: String, specificTagResolvers: List<TagResolver> = listOf()): TextContent =
    TextContent(
        renderTemplate(templateName, specificTagResolvers),
        ContentType.Text.Html,
    )

suspend fun ApplicationCall.respondHtml(htmlContent: String, status: HttpStatusCode? = null) {
    respondText(htmlContent, ContentType.Text.Html, status)
}

suspend fun ApplicationCall.respondNotFoundPage() {
    respondText(
        text = simplePageRenderer.renderTemplate("errors/404"),
        contentType = ContentType.Text.Html,
        status = HttpStatusCode.NotFound,
    )
}

suspend fun ApplicationCall.respondInternalErrorPage() {
    respondText(
        text = simplePageRenderer.renderTemplate("errors/500"),
        contentType = ContentType.Text.Html,
        status = HttpStatusCode.InternalServerError,
    )
}
