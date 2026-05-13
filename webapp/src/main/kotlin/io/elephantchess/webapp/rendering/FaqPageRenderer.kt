package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.utils.ResourceUtils.resourceAsString

private const val FAQ_TEMPLATE_NAME = "about/faq"
private const val FAQ_TEMPLATE_PATH = "/templates/$FAQ_TEMPLATE_NAME.html"
private const val FAQ_TOC_TAG_NAME = "faq_toc"

// Match `<h1 ... id="..." ...>inner</h1>` across lines, capturing id and inner content.
private val H1_WITH_ID_REGEX = Regex(
    """<h1\b[^>]*\bid\s*=\s*"([^"]+)"[^>]*>([\s\S]*?)</h1>""",
    RegexOption.IGNORE_CASE
)
private val INNER_TAG_REGEX = Regex("<[^>]+>")
private val WHITESPACE_REGEX = Regex("\\s+")

class FaqPageRenderer(private val simplePageRenderer: SimplePageRenderer) {

    suspend fun renderFaqPage(): String =
        simplePageRenderer.renderTemplate(FAQ_TEMPLATE_NAME, listOf(faqTocTagResolver()))

    private fun faqTocTagResolver(): TagResolver =
        CallbackTagResolver(FAQ_TOC_TAG_NAME) {
            val html = resourceAsString(FAQ_TEMPLATE_PATH) ?: return@CallbackTagResolver ""
            renderFaqToc(html)
        }

    /**
     * Parses the FAQ HTML and returns the table of content as an HTML fragment.
     */
    private fun renderFaqToc(html: String): String {
        val entries = H1_WITH_ID_REGEX.findAll(html)
            .map { match ->
                val id = match.groupValues[1].trim()
                val title = match.groupValues[2]
                    .replace(INNER_TAG_REGEX, "")
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
                id to title
            }
            .filter { (id, title) -> id.isNotEmpty() && title.isNotEmpty() }
            .toList()

        if (entries.isEmpty()) return ""
        val items = entries.joinToString("\n") { (id, title) ->
            """        <li><a href="#${escapeHtmlAttr(id)}">${escapeHtml(title)}</a></li>"""
        }
        return """<nav id="faq-toc">
    <h2>Table of Contents</h2>
    <ul>
$items
    </ul>
</nav>"""
    }

}
