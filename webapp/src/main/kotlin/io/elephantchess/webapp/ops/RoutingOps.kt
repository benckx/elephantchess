package io.elephantchess.webapp.ops

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun SimplePageRenderer.renderTemplateHtml(
    templateName: String,
    specificTagResolvers: List<TagResolver> = listOf()
): TextContent {
    return TextContent(
        renderTemplate(templateName, specificTagResolvers),
        ContentType.Text.Html
    )
}

suspend fun ApplicationCall.respondHtml(htmlContent: String) {
    respondText(htmlContent, ContentType.Text.Html)
}
