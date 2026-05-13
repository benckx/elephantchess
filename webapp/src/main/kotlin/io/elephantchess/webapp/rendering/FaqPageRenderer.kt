package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.utils.ResourceUtils.resourceAsString

class FaqPageRenderer(private val simplePageRenderer: SimplePageRenderer) {

    suspend fun renderFaqPage(): String =
        simplePageRenderer.renderTemplate(FAQ_TEMPLATE_NAME, listOf(faqTocTagResolver()))

    private fun faqTocTagResolver(): TagResolver =
        CallbackTagResolver("faq_toc") {
            val html = resourceAsString(FAQ_TEMPLATE_PATH)
                ?: throw IllegalStateException("FAQ template not found at $FAQ_TEMPLATE_PATH")

            renderFaqToc(html)
        }

    private companion object {

        const val FAQ_TEMPLATE_NAME = "about/faq"
        const val FAQ_TEMPLATE_PATH = "/templates/$FAQ_TEMPLATE_NAME.html"

        // Match `<h1 ... id="..." ...>inner</h1>` across lines, capturing id and inner content.
        val H1_WITH_ID_REGEX = Regex("""<h1\b[^>]*\bid\s*=\s*"([^"]+)"[^>]*>([\s\S]*?)</h1>""", RegexOption.IGNORE_CASE)
        val INNER_TAG_REGEX = Regex("<[^>]+>")
        val WHITESPACE_REGEX = Regex("\\s+")

        /**
         * Parses the FAQ HTML and returns the table of content as an HTML fragment.
         */
        fun renderFaqToc(html: String): String {
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
                """<li><a href="#${escapeHtmlAttr(id)}">${escapeHtml(title)}</a></li>"""
            }
            return """<nav id="faq-toc"><ul>$items</ul></nav>"""
        }

    }

}
