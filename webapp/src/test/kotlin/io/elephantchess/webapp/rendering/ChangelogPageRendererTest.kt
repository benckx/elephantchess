package io.elephantchess.webapp.rendering

import io.elephantchess.webapp.rendering.ChangelogPageRenderer.Companion.renderChangelogToc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangelogPageRendererTest {

    @Test
    fun `empty input produces no toc`() {
        assertEquals("", renderChangelogToc(""))
        assertEquals("", renderChangelogToc("<h3>no id here</h3>"))
    }

    @Test
    fun `lists each month heading in document order`() {
        val html = """
            <h3 id="2026-05">May 2026</h3>
            <h4>2026-05-17</h4>
            <h3 id="2026-04">April 2026</h3>
            <h4>2026-04-20</h4>
            <h3 id="2025-12">December 2025</h3>
            <h4>2025-12-29</h4>
        """.trimIndent()

        val toc = renderChangelogToc(html)

        assertTrue(toc.startsWith("""<nav id="faq-toc">"""))
        assertTrue(toc.endsWith("</nav>"))

        val mayIdx = toc.indexOf("""<a href="#2026-05">May 2026</a>""")
        val aprIdx = toc.indexOf("""<a href="#2026-04">April 2026</a>""")
        val decIdx = toc.indexOf("""<a href="#2025-12">December 2025</a>""")
        assertTrue(mayIdx >= 0, "expected May 2026 link in TOC")
        assertTrue(aprIdx > mayIdx, "expected April 2026 after May 2026")
        assertTrue(decIdx > aprIdx, "expected December 2025 after April 2026")
    }

    @Test
    fun `ignores h3 without year-month id`() {
        val html = """
            <h3 id="some-section">Heading</h3>
            <h3 id="2026-05">May 2026</h3>
        """.trimIndent()

        val toc = renderChangelogToc(html)
        assertTrue(toc.contains("""<a href="#2026-05">May 2026</a>"""))
        assertTrue(!toc.contains("some-section"))
    }

    @Test
    fun `renders TOC from the real changelog template`() {
        val html = this::class.java.getResource("/templates/about/changelog.html")?.readText()
            ?: error("changelog template not found on test classpath")

        val toc = renderChangelogToc(html)

        assertTrue(toc.isNotBlank(), "TOC should not be blank for the real changelog")
        assertTrue(toc.startsWith("""<nav id="faq-toc">"""))
        // The very first month heading in the template is the most recent one.
        val firstH3 = Regex("""<h3\b[^>]*\bid\s*=\s*"(\d{4}-\d{2})"""").find(html)
        assertTrue(firstH3 != null, "expected at least one month <h3> in the changelog template")
        assertTrue(toc.contains("""href="#${firstH3.groupValues[1]}""""))
    }

}
