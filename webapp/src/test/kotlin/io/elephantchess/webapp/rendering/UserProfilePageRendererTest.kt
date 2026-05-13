package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.servicelayer.dto.user.UserProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class UserProfilePageRendererTest {

    private val pageRenderer = UserProfilePageRenderer(
        HtmlRenderer(
            isMinificationEnabled = false,
            cdnFolder = null,
            webTemplateRenderer = WebTemplateRenderer()
        )
    )

    @Test
    fun `blank description renders empty-placeholder text`() = runTest {
        val html = pageRenderer.renderUserProfile(
            UserProfile(
                userId = "user-id",
                username = "JiangHu",
                country = null,
                profileDescription = "   ",
                puzzleRating = 1500
            )
        )

        assertTrue(
            html.contains("""<div id="profile-description" class="empty-block-placeholder">"""),
            "Expected empty placeholder block in output: $html"
        )
        assertTrue(
            html.contains("JiangHu has not filled their description yet."),
            "Expected fallback text in output: $html"
        )
    }

    @Test
    fun `non-blank description still renders description paragraphs`() = runTest {
        val html = pageRenderer.renderUserProfile(
            UserProfile(
                userId = "user-id",
                username = "JiangHu",
                country = null,
                profileDescription = "First line",
                puzzleRating = 1500
            )
        )

        assertTrue(
            html.contains("""<div id="profile-description"><p>First line</p></div>"""),
            "Expected rendered description block in output: $html"
        )
    }
}
