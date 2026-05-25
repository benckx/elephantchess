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
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr

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
                gameStatsTableTagResolver(gameStats),
            )
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

    private fun gameStatsTableTagResolver(gameStats: TimeCategoryStatsResponse): TagResolver {
        data class Category(
            val key: String,
            val label: String,
            val iconPath: String,
            val iconAlt: String,
            val rating: Int,
            val wins: Int,
            val draws: Int,
            val losses: Int,
        )

        val categories = listOf(
            Category(
                key = "bullet",
                label = "Bullet",
                iconPath = "/images/icons/shuttle.png",
                iconAlt = "bullet",
                rating = gameStats.ratings.bullet,
                wins = gameStats.pvp.bullet.wins,
                draws = gameStats.pvp.bullet.draws,
                losses = gameStats.pvp.bullet.losses
            ),
            Category(
                key = "blitz",
                label = "Blitz",
                iconPath = "/images/icons/flash-squared.png",
                iconAlt = "blitz",
                rating = gameStats.ratings.blitz,
                wins = gameStats.pvp.blitz.wins,
                draws = gameStats.pvp.blitz.draws,
                losses = gameStats.pvp.blitz.losses
            ),
            Category(
                key = "rapid",
                label = "Rapid",
                iconPath = "/images/icons/run.png",
                iconAlt = "rapid",
                rating = gameStats.ratings.rapid,
                wins = gameStats.pvp.rapid.wins,
                draws = gameStats.pvp.rapid.draws,
                losses = gameStats.pvp.rapid.losses
            ),
            Category(
                key = "classical",
                label = "Classical",
                iconPath = "/images/icons/museum.png",
                iconAlt = "classical",
                rating = gameStats.ratings.classical,
                wins = gameStats.pvp.classical.wins,
                draws = gameStats.pvp.classical.draws,
                losses = gameStats.pvp.classical.losses
            ),
            Category(
                key = "correspondence",
                label = "Correspondence",
                iconPath = "/images/icons/email.png",
                iconAlt = "correspondence",
                rating = gameStats.ratings.correspondence,
                wins = gameStats.pvp.correspondence.wins,
                draws = gameStats.pvp.correspondence.draws,
                losses = gameStats.pvp.correspondence.losses
            ),
        )

        return KtorHtmlBuilderTagResolver("profile_game_stats_table") {
            table {
                id = "ratings-table"

                tr {
                    th { +"category" }
                    categories.forEach { category ->
                        td {
                            img(
                                alt = category.iconAlt,
                                src = category.iconPath,
                                classes = "time-control-icons time-control-icons-larger"
                            ) {
                                id = "rating-${category.key}-icon"
                            }
                        }
                    }
                }
                tr("category-label-row") {
                    th {}
                    categories.forEach { category -> th { +category.label } }
                }
                tr {
                    th { +"rating" }
                    categories.forEach { category ->
                        td {
                            span {
                                id = "rating-${category.key}"
                                +category.rating.toString()
                            }
                        }
                    }
                }
                tr {
                    th { +"W/D/L" }
                    categories.forEach { category ->
                        td("wdl-cell") {
                            span("wdl-win") { +"W ${category.wins}" }
                            +" / "
                            span("wdl-draw") { +"D ${category.draws}" }
                            +" / "
                            span("wdl-loss") { +"L ${category.losses}" }
                        }
                    }
                }
            }
        }
    }

    suspend fun renderUserBrowsePvpGames(username: String): String {
        return htmlRenderer.renderHtml(
            "/templates/user_browse_pvp_games.html",
            specificTagResolvers = listOf(SimpleValueTagResolver("username", username))
        )
    }

}
