package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.servicelayer.dto.user.GameStatsResponse
import io.elephantchess.servicelayer.dto.user.NumberOfGamesPerTimeCategory
import io.elephantchess.servicelayer.dto.user.NumberOfOutcomes
import io.elephantchess.servicelayer.dto.user.RatingsPerTimeCategory
import io.elephantchess.servicelayer.dto.user.UserProfile
import io.elephantchess.servicelayer.services.UserProfileAnalyticsService
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfilePageRendererTest {

    private val userProfileAnalyticsService = mock<UserProfileAnalyticsService>()
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
    private val gameStats = GameStatsResponse(
        ratings = RatingsPerTimeCategory(1000, 1001, 1002, 1003, 1004, 1005),
        pvp = NumberOfGamesPerTimeCategory(
            bullet = NumberOfOutcomes(1, 2, 3),
            blitz = NumberOfOutcomes(4, 5, 6),
            rapid = NumberOfOutcomes(7, 8, 9),
            classical = NumberOfOutcomes(10, 11, 12),
            severalDays = NumberOfOutcomes(13, 14, 15),
            correspondence = NumberOfOutcomes(16, 17, 18),
        ),
    )

    @Test
    fun `renderUserProfile includes profile picture when available`() = runTest {
        whenever(userProfileAnalyticsService.fetchGameRatings("user-1")).thenReturn(gameStats)
        val renderer = UserProfilePageRenderer(htmlRenderer, userProfileAnalyticsService)

        val html = renderer.renderUserProfile(
            UserProfile(
                userId = "user-1",
                username = "alice",
                country = "BE",
                profileDescription = "hello",
                puzzleRating = 1200,
                profilePictureUrl = "https://cdn.elephantchess.io/local-backup/profile-pictures/user-1.png",
            )
        )

        assertTrue(html.contains("""id="profile-picture""""))
        assertTrue(html.contains("""src="https://cdn.elephantchess.io/local-backup/profile-pictures/user-1.png""""))
    }

    @Test
    fun `renderUserProfile omits profile picture when unavailable`() = runTest {
        whenever(userProfileAnalyticsService.fetchGameRatings("user-1")).thenReturn(gameStats)
        val renderer = UserProfilePageRenderer(htmlRenderer, userProfileAnalyticsService)

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
