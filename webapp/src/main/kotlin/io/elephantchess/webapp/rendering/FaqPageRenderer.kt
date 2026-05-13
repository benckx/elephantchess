package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.utils.ResourceUtils.resourceAsString

internal const val FAQ_TEMPLATE_NAME = "about/faq"
internal const val FAQ_TEMPLATE_PATH = "/templates/$FAQ_TEMPLATE_NAME.html"
internal const val FAQ_TOC_TAG_NAME = "faq_toc"

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

    companion object {

        internal fun faqTocTagResolver(templatePath: String = FAQ_TEMPLATE_PATH): TagResolver =
            CallbackTagResolver(FAQ_TOC_TAG_NAME) {
                val html = resourceAsString(templatePath) ?: return@CallbackTagResolver ""
                renderFaqToc(html)
            }

        /**
         * Parses the FAQ HTML and returns the table of content as an HTML fragment.
         * Visible for tests.
         */
        internal fun renderFaqToc(html: String): String {
            val entries = extractFaqTocEntries(html)
            if (entries.isEmpty()) return ""
            val items = entries.joinToString("\n") { (id, title) ->
                """        <li><a href="#${escapeHtmlAttr(id)}">${escapeHtmlText(title)}</a></li>"""
            }
            return """<nav id="faq-toc">
    <h2>Table of Contents</h2>
    <ul>
$items
    </ul>
</nav>"""
        }

        /**
         * Extracts every `<h1 id="...">Title</h1>` entry from the given HTML, in order.
         * The title is stripped of any inner HTML tags and trimmed.
         */
        internal fun extractFaqTocEntries(html: String): List<Pair<String, String>> {
            return H1_WITH_ID_REGEX.findAll(html)
                .map { match ->
                    val id = match.groupValues[1].trim()
                    val title = stripInnerTags(match.groupValues[2]).trim()
                    id to title
                }
                .filter { (id, title) -> id.isNotEmpty() && title.isNotEmpty() }
                .toList()
        }

        private fun stripInnerTags(html: String): String =
            html.replace(INNER_TAG_REGEX, "").replace(WHITESPACE_REGEX, " ")

        private fun escapeHtmlText(value: String): String =
            value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

        private fun escapeHtmlAttr(value: String): String =
            escapeHtmlText(value)
                .replace("\"", "&quot;")
    }

}
