package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.KtorHtmlBuilderTagResolver
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.servicelayer.dto.user.UserProfile
import io.elephantchess.utils.cropToFirstNWords
import io.ktor.http.encodeURLPath
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.p

class UserProfilePageRenderer(private val htmlRenderer: HtmlRenderer) {

    suspend fun renderUserProfile(userProfile: UserProfile): String {
        val username = userProfile.username
        val description = userProfile.profileDescription
        val countryCode = userProfile.country?.lowercase()

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
                    img("flag-icons") {
                        id = "profile-flag"
                        src = "/images/flags/$countryCode.svg"
                        alt = countryCode
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
                    formatNewLinesToHtmlParagraphs(description).forEach { paragraph ->
                        p {
                            +paragraph
                        }
                    }
                }
            } else {
                div("empty-block-placeholder") {
                    id = "profile-description"
                    +"$username has not filled their description yet."
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
