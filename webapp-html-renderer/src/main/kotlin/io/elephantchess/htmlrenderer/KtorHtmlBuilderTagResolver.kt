package io.elephantchess.htmlrenderer

import kotlinx.html.TagConsumer
import kotlinx.html.stream.appendHTML

/**
 * Resolves a template tag by rendering HTML content with the Ktor/kotlinx.html DSL.
 */
class KtorHtmlBuilderTagResolver(
    override val tagName: String,
    private val htmlBuilder: TagConsumer<StringBuilder>.() -> Unit,
) : TagResolver {

    override suspend fun resolveContent() = listOf(
        buildString {
            appendHTML().run(htmlBuilder)
        }
    )

}
