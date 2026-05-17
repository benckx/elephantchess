package io.elephantchess.webapp.rendering

import io.elephantchess.webapp.rendering.ChangelogPageRenderer.Companion.renderChangelogToc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangelogPageRendererTest {

    @Test
    fun `empty input produces no toc`() {
        assertEquals("", renderChangelogToc(""))
        assertEquals("", renderChangelogToc("<h1>no id here</h1>"))
    }

    @Test
    fun `groups months by quarter in document order`() {
        val html = """
            <h1 id="2026-05">May 2026</h1>
            <h4>2026-05-17</h4>
            <h1 id="2026-04">April 2026</h1>
            <h4>2026-04-20</h4>
            <h1 id="2026-03">March 2026</h1>
            <h4>2026-03-15</h4>
            <h1 id="2025-12">December 2025</h1>
            <h4>2025-12-29</h4>
        """.trimIndent()

        val toc = renderChangelogToc(html)

        assertTrue(toc.startsWith("""<nav id="page-toc">"""))
        assertTrue(toc.endsWith("</nav>"))

        // Q2 2026 should link to the first month of that quarter in document order (May).
        val q2_2026 = toc.indexOf("""<a href="#2026-05">Q2 2026</a>""")
        // Q1 2026 should link to its first month in document order (March).
        val q1_2026 = toc.indexOf("""<a href="#2026-03">Q1 2026</a>""")
        // Q4 2025 should link to December 2025.
        val q4_2025 = toc.indexOf("""<a href="#2025-12">Q4 2025</a>""")
        assertTrue(q2_2026 >= 0, "expected Q2 2026 link in TOC")
        assertTrue(q1_2026 > q2_2026, "expected Q1 2026 after Q2 2026")
        assertTrue(q4_2025 > q1_2026, "expected Q4 2025 after Q1 2026")
        // April 2026 belongs to Q2 2026 and should not produce its own entry.
        assertTrue(!toc.contains("#2026-04"), "expected only one entry per quarter")
    }

    @Test
    fun `ignores h1 without year-month id`() {
        val html = """
            <h1 id="some-section">Heading</h1>
            <h1 id="2026-05">May 2026</h1>
        """.trimIndent()

        val toc = renderChangelogToc(html)
        assertTrue(toc.contains("""<a href="#2026-05">Q2 2026</a>"""))
        assertTrue(!toc.contains("some-section"))
    }

    @Test
    fun `renders TOC from the real changelog template`() {
        val html = this::class.java.getResource("/templates/about/changelog.html")?.readText()
            ?: error("changelog template not found on test classpath")

        val toc = renderChangelogToc(html)

        assertTrue(toc.isNotBlank(), "TOC should not be blank for the real changelog")
        assertTrue(toc.startsWith("""<nav id="page-toc">"""))
        // The very first month heading in the template is the most recent one; its quarter
        // entry should link to it.
        val firstH1 = Regex("""<h1\b[^>]*\bid\s*=\s*"(\d{4})-(\d{2})"""").find(html)
        assertTrue(firstH1 != null, "expected at least one month <h1> in the changelog template")
        val (year, month) = firstH1.groupValues[1] to firstH1.groupValues[2]
        val quarter = (month.toInt() - 1) / 3 + 1
        assertTrue(toc.contains("""<a href="#$year-$month">Q$quarter $year</a>"""))
    }

}
