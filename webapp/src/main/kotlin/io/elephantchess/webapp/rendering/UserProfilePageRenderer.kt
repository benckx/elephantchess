package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.servicelayer.dto.user.UserProfile
import io.elephantchess.utils.cropToFirstNWords

class UserProfilePageRenderer(private val htmlRenderer: HtmlRenderer) {

    suspend fun renderUserProfile(userProfile: UserProfile): String {
        val username = userProfile.username
        val description = userProfile.profileDescription
        val countryCode = userProfile.country?.lowercase()

        return htmlRenderer.renderHtml(
            templatePath = "/templates/user_profile.html",
            specificTagResolvers = listOf(
                noIndexMeta(description),
                SimpleValueTagResolver("user_id", userProfile.userId),
                SimpleValueTagResolver("username", userProfile.username),
                descriptionMeta(username, description),
                profilePictureTagResolver(userProfile.profilePictureUrl, username),
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
        return CallbackTagResolver("flag_header_panel") {
            if (countryCode != null) {
                """<div id="flag-header-panel" class="profile-header-panel" data-country-code="$countryCode">
                    |<img id="profile-flag" class="flag-icons" src="/images/flags/$countryCode.svg" alt="$countryCode"/>
                    |</div>""".trimMargin()
            } else {
                ""
            }
        }
    }

    private fun profilePictureTagResolver(profilePictureUrl: String?, username: String): TagResolver {
        return CallbackTagResolver("profile_picture_panel") {
            if (profilePictureUrl != null) {
                val escapedUsername = escapeHtml(username)
                """<div class="profile-header-panel profile-picture-header-panel">
                    |<img id="profile-picture" src="$profilePictureUrl" alt="$escapedUsername profile picture"/>
                    |</div>""".trimMargin()
            } else {
                ""
            }
        }
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun descriptionDivTagResolver(username: String, description: String?): TagResolver {
        return CallbackTagResolver("user_profile_description") {
            if (description != null) {
                buildString {
                    append("""<div id="profile-description">""")
                    append(formatNewLinesToHtmlParagraphs(description))
                    append("""</div>""")
                }
            } else {
                buildString {
                    append("""<div id="profile-description" class="empty-block-placeholder">""")
                    append("$username has not filled their description yet.")
                    append("""</div>""")
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
