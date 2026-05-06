package io.elephantchess.htmlrenderer

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateRendererTest {

    private class TestRenderer(
        baseTagResolvers: List<TagResolver> = listOf(),
        disabledTemplates: List<String> = listOf(),
    ) : TemplateRenderer(baseTagResolvers, disabledTemplates)

    private class GameLinkTagResolver(
        private val webHost: String,
        private val gameId: String
    ) : TagResolver {
        override val tagName = "game_link"
        override suspend fun resolveContent(): List<String> {
            val url = "$webHost/game?id=$gameId"
            return listOf("""<a href="$url">$url</a>""")
        }
    }

    @Test
    fun `single tag on a line`() = runTest {
        val renderer = TestRenderer()
        val result = renderer.render(
            "Hello {{name}}!",
            specificTagResolvers = listOf(SimpleValueTagResolver("name", "Alice"))
        )
        assertEquals("Hello Alice!", result)
    }

    @Test
    fun `two tags on a line`() = runTest {
        val renderer = TestRenderer()
        val result = renderer.render(
            "{{opponent}} {{verb1}} the draw while you were offline.",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("opponent", "Albus"),
                SimpleValueTagResolver("verb1", "declined"),
            )
        )
        assertEquals("Albus declined the draw while you were offline.", result)
    }

    @Test
    fun `two tags on a line with anchor`() = runTest {
        val renderer = TestRenderer()

        val result = renderer.render(
            "{{verb2}} the game at {{game_link}}<br/>",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("verb2", "Resume"),
                GameLinkTagResolver("https://elephantchess.io", "42"),
            )
        )
        assertEquals(
            """Resume the game at <a href="https://elephantchess.io/game?id=42">https://elephantchess.io/game?id=42</a><br/>""",
            result
        )
    }

    @Test
    fun `opponent_responded_to_draw_while_offline template`() = runTest {
        val cssResolver = object : TagResolver {
            override val tagName = "email_css"
            override suspend fun resolveContent() = listOf("<style>body{}</style>")
        }
        val updateMailSettingsResolver = object : TagResolver {
            override val tagName = "update_mail_settings"
            override suspend fun resolveContent() =
                listOf("""You can update your email notifications settings at <a href="https://elephantchess.io/user/settings">https://elephantchess.io/user/settings</a>""")
        }

        val template = """
            |{{email_css}}
            |{{opponent}} {{verb1}} the draw while you were offline.<br/>
            |{{verb2}} the game at {{game_link}}<br/>
            |<br/>
            |{{update_mail_settings}}
        """.trimMargin()

        val renderer = TestRenderer()
        val result = renderer.render(
            template,
            specificTagResolvers = listOf(
                cssResolver,
                SimpleValueTagResolver("opponent", "Albus"),
                SimpleValueTagResolver("verb1", "declined"),
                SimpleValueTagResolver("verb2", "Resume"),
                GameLinkTagResolver("https://elephantchess.io", "42"),
                updateMailSettingsResolver,
            )
        )

        val expected = """
            |<style>body{}</style>
            |Albus declined the draw while you were offline.<br/>
            |Resume the game at <a href="https://elephantchess.io/game?id=42">https://elephantchess.io/game?id=42</a><br/>
            |<br/>
            |You can update your email notifications settings at <a href="https://elephantchess.io/user/settings">https://elephantchess.io/user/settings</a>
        """.trimMargin()

        assertEquals(expected, result)
    }

    @Test
    fun `three tags on a line`() = runTest {
        val renderer = TestRenderer()
        val result = renderer.render(
            "{{a}} {{b}} {{c}}",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("a", "1"),
                SimpleValueTagResolver("b", "2"),
                SimpleValueTagResolver("c", "3"),
            )
        )
        assertEquals("1 2 3", result)
    }

    @Test
    fun `multi-line resolver propagates indentation by default`() = runTest {
        val renderer = TestRenderer()
        val multiLineResolver = object : TagResolver {
            override val tagName = "block"
            override suspend fun resolveContent() = listOf("line1", "line2", "line3")
        }
        val result = renderer.render(
            "    {{block}}",
            specificTagResolvers = listOf(multiLineResolver)
        )
        val expected = """
            |    line1
            |    line2
            |    line3
        """.trimMargin()
        assertEquals(expected, result)
    }

    @Test
    fun `keepIndentation false strips leading indent on multi-line resolver`() = runTest {
        val renderer = TestRenderer()
        val multiLineResolver = object : TagResolver {
            override val tagName = "block"
            override suspend fun resolveContent() = listOf("line1", "line2", "line3")
        }
        val result = renderer.render(
            "    {{block}}[[keepIndentation:false]]",
            specificTagResolvers = listOf(multiLineResolver)
        )
        val expected = """
            |line1
            |line2
            |line3
        """.trimMargin()
        assertEquals(expected, result)
    }

    @Test
    fun `iterations keyword form is equivalent to legacy Nx form`() = runTest {
        val renderer = TestRenderer()
        val resolver = object : TagResolver {
            override val tagName = "item"
            override suspend fun resolveContent() = listOf("<li>", "x", "</li>")
        }
        val legacy = renderer.render("{{item}}[[3x]]", listOf(resolver))
        val keyword = renderer.render("{{item}}[[iterations:3]]", listOf(resolver))
        assertEquals(legacy, keyword)
        assertEquals(9, keyword.lines().size)
    }

    @Test
    fun `blank lines from multi-line resolver are preserved`() = runTest {
        val renderer = TestRenderer()
        val resolver = object : TagResolver {
            override val tagName = "block"
            override suspend fun resolveContent() = listOf("a", "", "b")
        }
        val result = renderer.render("{{block}}[[keepIndentation:false]]", listOf(resolver))
        assertEquals("a\n\nb", result)
    }

    @Test
    fun `tag that inlines to empty drops its line`() = runTest {
        val renderer = TestRenderer()
        val result = renderer.render(
            "before\n{{x}}\nafter",
            listOf(SimpleValueTagResolver("x", ""))
        )
        assertEquals("before\nafter", result)
    }

}
