package io.elephantchess.htmlrenderer

import kotlinx.coroutines.test.runTest
import kotlinx.html.a
import kotlinx.html.p
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorHtmlBuilderTagResolverTest {

    @Test
    fun `resolves html content from builder`() = runTest {
        val url = "https://elephantchess.io/game?id=42"
        val resolver = KtorHtmlBuilderTagResolver("game_link") {
            a(href = url) { +url }
        }

        assertEquals(listOf("""<a href="$url">$url</a>"""), resolver.resolveContent())
    }

    @Test
    fun `resolves non-anchor html fragments`() = runTest {
        val resolver = KtorHtmlBuilderTagResolver("paragraph") {
            p { +"Hello from builder" }
        }

        assertEquals(listOf("<p>Hello from builder</p>\n"), resolver.resolveContent())
    }

}
