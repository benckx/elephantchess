package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.ResourceTagResolver

class WebFragmentResolver(override val tagName: String) : ResourceTagResolver(tagName) {

    override fun resolvePath(tagName: String): String = "/web_fragments/$tagName.html"

}
