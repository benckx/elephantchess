package io.elephantchess.webapp.ops

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*

private val simplePageRenderer by koin<SimplePageRenderer>()
private val supportedErrorPages = listOf(401, 403, 404, 429, 500)

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

suspend fun ApplicationCall.respondTooManyRequestsPage() {
    respondText(
        text = simplePageRenderer.renderTemplate("errors/429"),
        contentType = ContentType.Text.Html,
        status = HttpStatusCode.TooManyRequests,
    )
}

suspend fun ApplicationCall.respondInternalErrorPage() {
    respondText(
        text = simplePageRenderer.renderTemplate("errors/500"),
        contentType = ContentType.Text.Html,
        status = HttpStatusCode.InternalServerError,
    )
}

suspend fun ApplicationCall.respondErrorPage(statusCode: HttpStatusCode) {
    val actualCode =
        if (statusCode.value !in supportedErrorPages) {
            500
        } else {
            statusCode.value
        }

    respondText(
        text = simplePageRenderer.renderTemplate("errors/$actualCode"),
        contentType = ContentType.Text.Html,
        status = HttpStatusCode.fromValue(actualCode),
    )
}
