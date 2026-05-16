package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.htmlrenderer.TemplateRenderer
import io.elephantchess.servicelayer.dto.user.UserProfile
import io.ktor.http.encodeURLPath
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfilePageRendererTest {

    private class TestTemplateRenderer : TemplateRenderer(
        baseTagResolvers = listOf(
            emptyTagResolver("header_init"),
            emptyTagResolver("apex_charts"),
            emptyTagResolver("body_init"),
        ),
        disabledTemplates = listOf("game_thumb"),
    )

    private suspend fun renderUserProfile(username: String): String {
        val renderer = UserProfilePageRenderer(
            HtmlRenderer(
                isMinificationEnabled = false,
                cdnFolder = null,
                webTemplateRenderer = TestTemplateRenderer(),
            )
        )
        return renderer.renderUserProfile(
            UserProfile(
                userId = "user-id",
                username = username,
                country = null,
                profileDescription = "Profile description",
                puzzleRating = 1234,
            )
        )
    }

    @Test
    fun `rendered user profile contains encoded canonical url`() = runTest {
        val username = "Đức李"
        val html = renderUserProfile(username)
        val expectedCanonicalUrl = "https://elephantchess.io/@/${username.encodeURLPath()}"

        assertTrue(html.contains("""rel="canonical""""))
        assertTrue(html.contains(expectedCanonicalUrl), "Expected encoded canonical URL in: $html")
        assertTrue(html.contains("""property="og:url""""))
        assertFalse(html.contains("https://elephantchess.io/@/$username"), "Canonical URL should be encoded: $html")
    }

    private companion object {
        fun emptyTagResolver(tagName: String) = SimpleValueTagResolver(tagName, "")
    }
}
