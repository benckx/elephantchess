package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.TagResolver

class CallbackTagResolver(
    override val tagName: String,
    private val fetch: suspend () -> String?
) : TagResolver {

    override suspend fun resolveContent() = listOf(fetch() ?: "")

}
