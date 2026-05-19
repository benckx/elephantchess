package io.elephantchess.htmlrenderer

import kotlinx.html.TagConsumer
import kotlinx.html.stream.createHTML

/**
 * Resolves a template tag by rendering HTML content with the Ktor/kotlinx.html DSL.
 */
class KtorHtmlBuilderTagResolver(
    override val tagName: String,
    private val htmlBuilder: TagConsumer<String>.() -> String,
) : TagResolver {

    override suspend fun resolveContent() = listOf(createHTML().htmlBuilder())

}
