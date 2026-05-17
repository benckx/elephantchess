package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.utils.ResourceUtils.resourceAsString

class ChangelogPageRenderer(private val simplePageRenderer: SimplePageRenderer) {

    suspend fun renderChangelogPage(): String =
        simplePageRenderer.renderTemplate(CHANGELOG_TEMPLATE_NAME, listOf(changelogTocTagResolver()))

    private fun changelogTocTagResolver(): TagResolver =
        CallbackTagResolver("changelog_toc") {
            val html = resourceAsString(CHANGELOG_TEMPLATE_PATH)
                ?: throw IllegalStateException("Changelog template not found at $CHANGELOG_TEMPLATE_PATH")

            renderChangelogToc(html)
        }

    companion object {

        const val CHANGELOG_TEMPLATE_NAME = "about/changelog"
        const val CHANGELOG_TEMPLATE_PATH = "/templates/$CHANGELOG_TEMPLATE_NAME.html"

        // Match `<h3 ... id="YYYY-MM" ...>inner</h3>`, capturing the id (the year-month) and the label.
        val H3_MONTH_WITH_ID_REGEX =
            Regex("""<h3\b[^>]*\bid\s*=\s*"(\d{4}-\d{2})"[^>]*>([\s\S]*?)</h3>""", RegexOption.IGNORE_CASE)
        val INNER_TAG_REGEX = Regex("<[^>]+>")
        val WHITESPACE_REGEX = Regex("\\s+")

        /**
         * Parses the changelog HTML and returns the table of content as an HTML fragment.
         * The TOC lists each month section (e.g. "May 2026") in the order they appear in the page.
         */
        fun renderChangelogToc(html: String): String {
            val entries = H3_MONTH_WITH_ID_REGEX.findAll(html)
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
            return """<nav id="changelog-toc"><ul>$items</ul></nav>"""
        }

    }

}
