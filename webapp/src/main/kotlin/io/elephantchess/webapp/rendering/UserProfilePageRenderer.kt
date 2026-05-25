package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.KtorHtmlBuilderTagResolver
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.servicelayer.dto.user.UserProfile
import io.elephantchess.servicelayer.dto.user.TimeCategoryStatsResponse
import io.elephantchess.servicelayer.services.UserProfileAnalyticsService
import io.elephantchess.utils.cropToFirstNWords
import io.ktor.http.encodeURLPath
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.p

class UserProfilePageRenderer(
    private val htmlRenderer: HtmlRenderer,
    private val userProfileAnalyticsService: UserProfileAnalyticsService
) {

    suspend fun renderUserProfile(userProfile: UserProfile): String {
        val username = userProfile.username
        val description = userProfile.profileDescription
        val countryCode = userProfile.country?.lowercase()
        val gameStats = userProfileAnalyticsService.fetchGameRatings(userProfile.userId)

        return htmlRenderer.renderHtml(
            templatePath = "/templates/user_profile.html",
            canonicalPath = "/@/${username.encodeURLPath()}",
            specificTagResolvers = listOf(
                noIndexMeta(description),
                SimpleValueTagResolver("user_id", userProfile.userId),
                SimpleValueTagResolver("username", userProfile.username),
                descriptionMeta(username, description),
                flagPanelTagResolver(countryCode),
                descriptionDivTagResolver(username, description),
            ) + gameStatsTagResolvers(gameStats)
        )
    }

    private fun noIndexMeta(description: String?): TagResolver {
        return CallbackTagResolver("meta_no_index_conditional") {
            if (description.isNullOrBlank()) {
                WebFragmentResolver("meta_no_index").resolveContent().firstOrNull() ?: ""
            } else {
                ""
            }
        }
    }

    fun descriptionMeta(username: String, description: String?): TagResolver {
        return CallbackTagResolver("description_meta") {
            var content = "$username on elephantchess.io"
            if (!description.isNullOrBlank()) {
                content += " - ${cropToFirstNWords(description, 100)}"
            }

            descriptionMeta(content)
        }
    }

    private fun flagPanelTagResolver(countryCode: String?): TagResolver {
        return KtorHtmlBuilderTagResolver("flag_header_panel") {
            if (!countryCode.isNullOrBlank() && !countryCode.equals("none", ignoreCase = true)) {
                div("profile-header-panel") {
                    id = "flag-header-panel"
                    attributes["data-country-code"] = countryCode
                    img(alt = countryCode, src = "/images/flags/$countryCode.svg", classes = "flag-icons") {
                        id = "profile-flag"
                    }
                }
            }
        }
    }

    private fun descriptionDivTagResolver(username: String, description: String?): TagResolver {
        return KtorHtmlBuilderTagResolver("user_profile_description") {
            if (!description.isNullOrBlank()) {
                div {
                    id = "profile-description"
                    description.toParagraphs().forEach { p { +it } }
                }
            } else {
                div("empty-block-placeholder") {
                    id = "profile-description"
                    +"$username has not filled their description yet."
                }
            }
        }
    }

    private fun gameStatsTagResolvers(gameStats: TimeCategoryStatsResponse): List<TagResolver> {
        return listOf(
            SimpleValueTagResolver("rating_bullet", gameStats.ratings.bullet.toString()),
            SimpleValueTagResolver("rating_blitz", gameStats.ratings.blitz.toString()),
            SimpleValueTagResolver("rating_rapid", gameStats.ratings.rapid.toString()),
            SimpleValueTagResolver("rating_classical", gameStats.ratings.classical.toString()),
            SimpleValueTagResolver("rating_correspondence", gameStats.ratings.correspondence.toString()),
            SimpleValueTagResolver("wins_bullet", gameStats.pvp.bullet.wins.toString()),
            SimpleValueTagResolver("wins_blitz", gameStats.pvp.blitz.wins.toString()),
            SimpleValueTagResolver("wins_rapid", gameStats.pvp.rapid.wins.toString()),
            SimpleValueTagResolver("wins_classical", gameStats.pvp.classical.wins.toString()),
            SimpleValueTagResolver("wins_correspondence", gameStats.pvp.correspondence.wins.toString()),
            SimpleValueTagResolver("losses_bullet", gameStats.pvp.bullet.losses.toString()),
            SimpleValueTagResolver("losses_blitz", gameStats.pvp.blitz.losses.toString()),
            SimpleValueTagResolver("losses_rapid", gameStats.pvp.rapid.losses.toString()),
            SimpleValueTagResolver("losses_classical", gameStats.pvp.classical.losses.toString()),
            SimpleValueTagResolver("losses_correspondence", gameStats.pvp.correspondence.losses.toString()),
            SimpleValueTagResolver("draws_bullet", gameStats.pvp.bullet.draws.toString()),
            SimpleValueTagResolver("draws_blitz", gameStats.pvp.blitz.draws.toString()),
            SimpleValueTagResolver("draws_rapid", gameStats.pvp.rapid.draws.toString()),
            SimpleValueTagResolver("draws_classical", gameStats.pvp.classical.draws.toString()),
            SimpleValueTagResolver("draws_correspondence", gameStats.pvp.correspondence.draws.toString()),
        )
    }

    suspend fun renderUserBrowsePvpGames(username: String): String {
        return htmlRenderer.renderHtml(
            "/templates/user_browse_pvp_games.html",
            specificTagResolvers = listOf(SimpleValueTagResolver("username", username))
        )
    }

}
