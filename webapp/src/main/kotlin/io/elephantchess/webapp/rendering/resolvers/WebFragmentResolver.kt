package io.elephantchess.webapp.rendering.resolvers

import io.elephantchess.htmlrenderer.ResourceTagResolver

class WebFragmentResolver(override val tagName: String, private val resourceName: String = tagName) : ResourceTagResolver(tagName) {

    override fun resolvePath(tagName: String): String =
        "/web_fragments/$resourceName.html"
}
