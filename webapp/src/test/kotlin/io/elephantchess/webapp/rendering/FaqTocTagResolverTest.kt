package io.elephantchess.webapp.rendering

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaqTocTagResolverTest {

    @Test
    fun `extracts h1 entries with id`() {
        val html = """
            <html><body>
                <h1 id="first">First Title</h1>
                <p>some text</p>
                <h1 id="second">Second <b>bold</b> title</h1>
                <h2 id="not-included">Ignored</h2>
                <h1>no id, ignored</h1>
                <h1 id="third">  Third  </h1>
            </body></html>
        """.trimIndent()

        val entries = extractFaqTocEntries(html)
        assertEquals(
            listOf(
                "first" to "First Title",
                "second" to "Second bold title",
                "third" to "Third",
            ),
            entries
        )
    }

    @Test
    fun `renderFaqToc generates anchor links`() {
        val html = """
            <h1 id="a">Alpha</h1>
            <h1 id="b">Beta</h1>
        """.trimIndent()

        val toc = renderFaqToc(html)
        assertTrue(toc.contains("""<a href="#a">Alpha</a>"""), "should contain link to #a")
        assertTrue(toc.contains("""<a href="#b">Beta</a>"""), "should contain link to #b")
        assertTrue(toc.contains("""<nav id="faq-toc">"""), "should wrap in <nav>")
    }

    @Test
    fun `renderFaqToc returns empty string when no h1 with id is found`() {
        assertEquals("", renderFaqToc("<p>nothing here</p>"))
        assertEquals("", renderFaqToc("<h1>no id</h1>"))
    }

    @Test
    fun `resolver runs against the real FAQ template and returns a non-empty TOC`() = runTest {
        val resolver = faqTocTagResolver()
        assertEquals(FAQ_TOC_TAG_NAME, resolver.tagName)
        val content = resolver.resolveContent().joinToString("")
        assertTrue(content.contains("""<nav id="faq-toc">"""), "TOC should be rendered: $content")
        assertTrue(content.contains("""href="#why-support-us""""), "TOC should link to 'why-support-us'")
        assertTrue(content.contains("""href="#auto-cancellation""""), "TOC should link to 'auto-cancellation'")
    }
}
