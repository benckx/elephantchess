package io.elephantchess.webapp.rendering

import io.elephantchess.utils.di.KoinSingleton

@KoinSingleton(eager = true)
class ChangelogPageRenderer(private val simplePageRenderer: SimplePageRenderer) {

    suspend fun renderChangelogPage(): String {
        val tocResolver =
            pageTocTagResolver(
                tagName = "changelog_toc",
                templatePath = CHANGELOG_TEMPLATE_PATH,
                extractEntries = ::changelogEntries,
            )

        return simplePageRenderer.renderTemplate(
            CHANGELOG_TEMPLATE_NAME,
            listOf(tocResolver),
        )
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
        private fun changelogEntries(html: String): List<Pair<String, String>> {
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

            return quarters.entries.map { (key, id) ->
                val (year, quarter) = key
                id to "Q$quarter $year"
            }
        }
    }
}
