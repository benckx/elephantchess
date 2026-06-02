package io.elephantchess.htmlrenderer

import kotlinx.coroutines.test.runTest
import kotlinx.html.a
import kotlinx.html.p
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KtorHtmlBuilderTagResolverTest {

    @Test
    fun `resolves html content from builder`() = runTest {
        val url = "https://elephantchess.io/game?id=42"
        val resolver = KtorHtmlBuilderTagResolver("game_link") {
            a(href = url) { +url }
        }

        val resolved = resolver.resolveContent()
        assertEquals(listOf("""<a href="$url">$url</a>"""), resolved)
        assertFalse(resolved.single().endsWith("\n"))
    }

    @Test
    fun `resolves non-anchor html fragments`() = runTest {
        val resolver = KtorHtmlBuilderTagResolver("paragraph") {
            p { +"Hello from builder" }
        }

        // kotlinx.html keeps a trailing newline for this block-level tag.
        assertEquals(listOf("<p>Hello from builder</p>\n"), resolver.resolveContent())
    }

}
