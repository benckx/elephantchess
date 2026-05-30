package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer.Companion.CDN_BASE
import io.elephantchess.htmlrenderer.KtorHtmlBuilderTagResolver
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.utils.ResourceUtils.resourceAsString
import kotlinx.html.link
import kotlinx.html.script

class BoardGuiExampleRenderer(private val simplePageRenderer: SimplePageRenderer) {

    suspend fun renderBoardGuiExample(useCdn: Boolean, templateName: String): String {
        return simplePageRenderer.renderTemplate(
            templateName,
            listOf(
                boardGuiAssetsTagResolver(useCdn),
                SimpleValueTagResolver(
                    "board_gui_assets_base_url_option",
                    if (useCdn) "${CDN_BASE}/static" else ""
                ),
                SimpleValueTagResolver("dist_version", DIST_VERSION),
                snippetTagResolver("snippet_setup", "board-gui-setup"),
                snippetTagResolver("snippet_headless", "board-gui-headless")
            )
        )
    }

    /**
     * Loads a raw HTML snippet file, and HTML-escapes the result so it can be safely inlined
     * as text inside a `<pre><code>` block.
     */
    private fun snippetTagResolver(tagName: String, snippetName: String): TagResolver =
        CallbackTagResolver(tagName) {
            val raw = resourceAsString("/templates/about/developers/snippets/$snippetName.html")
                ?: return@CallbackTagResolver ""

            raw
                .trim('\n')
                .let(::escapeHtml)
        }

    private fun boardGuiAssetsTagResolver(useCdn: Boolean): TagResolver {
        val distPath = if (useCdn) "${CDN_BASE}/dist/$DIST_VERSION" else "/dist/$DIST_VERSION"

        return KtorHtmlBuilderTagResolver("board_gui_assets") {
            link(rel = "stylesheet", href = "$distPath/board.min.css")
            script(src = "$distPath/xiangqi.min.js") { defer = true }
            script(src = "$distPath/board-gui.min.js") { defer = true }
        }
    }

    companion object {

        const val DIST_VERSION = "0.1.1"

    }

}
