package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.utils.ResourceUtils.resourceAsString
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

        // Match `<h4 ... id="YYYY-MM-DD" ...>inner</h4>`, capturing the id (the date).
        val H4_DATE_WITH_ID_REGEX =
            Regex("""<h4\b[^>]*\bid\s*=\s*"(\d{4}-\d{2}-\d{2})"[^>]*>[\s\S]*?</h4>""", RegexOption.IGNORE_CASE)

        private val MONTH_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

        /**
         * Parses the changelog HTML and returns the table of content as an HTML fragment.
         * Dates are clustered by month (e.g. "May 2026", "April 2026", etc.) preserving
         * the order in which they appear in the page (most recent first).
         */
        fun renderChangelogToc(html: String): String {
            val dates = H4_DATE_WITH_ID_REGEX.findAll(html)
                .mapNotNull { match ->
                    runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull()
                }
                .toList()

            if (dates.isEmpty()) return ""

            // Group by year-month, preserving first-seen order.
            val grouped = linkedMapOf<String, MutableList<LocalDate>>()
            for (date in dates) {
                val key = "%04d-%02d".format(date.year, date.monthValue)
                grouped.getOrPut(key) { mutableListOf() }.add(date)
            }

            val monthsHtml = grouped.entries.joinToString("\n") { (_, monthDates) ->
                val firstDate = monthDates.first()
                val monthLabel = firstDate.format(MONTH_LABEL_FORMATTER)
                val dateItems = monthDates.joinToString("\n") { date ->
                    val id = date.toString()
                    """<li><a href="#${escapeHtmlAttr(id)}">${escapeHtml(id)}</a></li>"""
                }
                """<li class="changelog-toc-month">${escapeHtml(monthLabel)}
                    |<ul class="changelog-toc-dates">
                    |$dateItems
                    |</ul>
                    |</li>""".trimMargin()
            }

            return """<nav id="changelog-toc"><ul>
                |$monthsHtml
                |</ul></nav>""".trimMargin()
        }

    }

}
