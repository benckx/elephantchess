package io.elephantchess.webapp.rendering

import io.elephantchess.webapp.rendering.ChangelogPageRenderer.Companion.renderChangelogToc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangelogPageRendererTest {

    @Test
    fun `empty input produces no toc`() {
        assertEquals("", renderChangelogToc(""))
        assertEquals("", renderChangelogToc("<h4>no id here</h4>"))
    }

    @Test
    fun `groups dates by month preserving order`() {
        val html = """
            <h4 id="2026-05-17">2026-05-17</h4>
            <h4 id="2026-05-16">2026-05-16</h4>
            <h4 id="2026-04-20">2026-04-20</h4>
            <h4 id="2026-04-19">2026-04-19</h4>
            <h4 id="2025-12-29">2025-12-29</h4>
        """.trimIndent()

        val toc = renderChangelogToc(html)

        assertTrue(toc.startsWith("""<nav id="changelog-toc">"""))
        assertTrue(toc.endsWith("</nav>"))

        // Month headers appear in the order they are encountered.
        val mayIdx = toc.indexOf("May 2026")
        val aprIdx = toc.indexOf("April 2026")
        val decIdx = toc.indexOf("December 2025")
        assertTrue(mayIdx >= 0, "expected May 2026 in TOC")
        assertTrue(aprIdx > mayIdx, "expected April 2026 after May 2026")
        assertTrue(decIdx > aprIdx, "expected December 2025 after April 2026")

        // Date links use the date id as anchor.
        assertTrue(toc.contains("""<a href="#2026-05-17">2026-05-17</a>"""))
        assertTrue(toc.contains("""<a href="#2026-04-19">2026-04-19</a>"""))
        assertTrue(toc.contains("""<a href="#2025-12-29">2025-12-29</a>"""))
    }

    @Test
    fun `ignores h4 without iso date id`() {
        val html = """
            <h4 id="some-section">Heading</h4>
            <h4 id="2026-05-17">2026-05-17</h4>
        """.trimIndent()

        val toc = renderChangelogToc(html)
        assertTrue(toc.contains("May 2026"))
        assertTrue(toc.contains("#2026-05-17"))
        assertTrue(!toc.contains("some-section"))
    }

    @Test
    fun `renders TOC from the real changelog template`() {
        val html = this::class.java.getResource("/templates/about/changelog.html")?.readText()
            ?: error("changelog template not found on test classpath")

        val toc = renderChangelogToc(html)

        assertTrue(toc.isNotBlank(), "TOC should not be blank for the real changelog")
        assertTrue(toc.startsWith("""<nav id="changelog-toc">"""))
        // The very first date in the template is the most recent one.
        val firstH4 = Regex("""<h4\b[^>]*\bid\s*=\s*"(\d{4}-\d{2}-\d{2})"""").find(html)
        assertTrue(firstH4 != null, "expected at least one dated <h4> in the changelog template")
        assertTrue(toc.contains("""href="#${firstH4.groupValues[1]}""""))
    }

}
