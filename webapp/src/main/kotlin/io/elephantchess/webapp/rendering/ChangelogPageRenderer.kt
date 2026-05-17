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

        // Match `<h1 ... id="YYYY-MM" ...>...</h1>`, capturing the year-month id.
        val H1_MONTH_WITH_ID_REGEX =
            Regex("""<h1\b[^>]*\bid\s*=\s*"(\d{4})-(\d{2})"[^>]*>[\s\S]*?</h1>""", RegexOption.IGNORE_CASE)

        /**
         * Parses the changelog HTML and returns the table of content as an HTML fragment, grouped
         * by quarter. Each TOC entry is a quarter label (e.g. "Q2 2026") that links to the first
         * month section belonging to that quarter in document order (i.e. the most recent month
         * of the quarter, since the changelog is reverse-chronological).
         */
        fun renderChangelogToc(html: String): String {
            val quarters = linkedMapOf<Pair<Int, Int>, String>() // (year, quarter) -> first month id
            H1_MONTH_WITH_ID_REGEX.findAll(html).forEach { match ->
                val year = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                if (month in 1..12) {
                    val quarter = (month - 1) / 3 + 1
                    val key = year to quarter
                    val id = "${match.groupValues[1]}-${match.groupValues[2]}"
                    quarters.putIfAbsent(key, id)
                }
            }

            if (quarters.isEmpty()) return ""
            val items = quarters.entries.joinToString("\n") { (key, id) ->
                val (year, quarter) = key
                val label = "Q$quarter $year"
                """<li><a href="#${escapeHtmlAttr(id)}">${escapeHtml(label)}</a></li>"""
            }
            return """<nav id="page-toc"><ul>$items</ul></nav>"""
        }

    }

}
