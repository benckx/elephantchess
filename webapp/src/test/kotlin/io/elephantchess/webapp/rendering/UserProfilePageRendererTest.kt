package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.servicelayer.dto.user.UserProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfilePageRendererTest {

    private val htmlRenderer = HtmlRenderer(
        isMinificationEnabled = false,
        cdnFolder = null,
        webTemplateRenderer = WebTemplateRenderer(
            baseTagResolvers = listOf(
                SimpleValueTagResolver("header_init", ""),
                SimpleValueTagResolver("body_init", ""),
                SimpleValueTagResolver("footer", ""),
                SimpleValueTagResolver("apex_charts", ""),
            )
        )
    )

    @Test
    fun `renderUserProfile includes profile picture when available`() = runTest {
        val renderer = UserProfilePageRenderer(htmlRenderer)

        val html = renderer.renderUserProfile(
            UserProfile(
                userId = "user-1",
                username = "alice",
                country = "BE",
                profileDescription = "hello",
                puzzleRating = 1200,
                profilePictureUrl = "https://cdn.elephantchess.io/local/profile-pictures/user-1.png",
            )
        )

        assertTrue(html.contains("""id="profile-picture""""))
        assertTrue(html.contains("""src="https://cdn.elephantchess.io/local/profile-pictures/user-1.png""""))
    }

    @Test
    fun `renderUserProfile omits profile picture when unavailable`() = runTest {
        val renderer = UserProfilePageRenderer(htmlRenderer)

        val html = renderer.renderUserProfile(
            UserProfile(
                userId = "user-1",
                username = "alice",
                country = null,
                profileDescription = null,
                puzzleRating = 1200,
            )
        )

        assertFalse(html.contains("""id="profile-picture""""))
    }
}
