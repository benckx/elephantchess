package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.KtorHtmlBuilderTagResolver
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.servicelayer.dto.user.NotificationsSettingsDto
import io.elephantchess.servicelayer.model.AuthenticatedToken
import io.elephantchess.servicelayer.model.VerifiedToken
import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.servicelayer.services.analytics.ARCHIVE_PAGE_VIEW_AFTER_DAYS
import io.elephantchess.utils.di.KoinSingleton
import io.elephantchess.webapp.rendering.resolvers.CallbackTagResolver
import io.elephantchess.webapp.rendering.resolvers.WebFragmentResolver
import kotlinx.html.*

@KoinSingleton(eager = true)
class UserSettingsPageRenderer(private val htmlRenderer: HtmlRenderer, private val userService: UserService) {

    suspend fun renderUserSettingsPage(token: VerifiedToken?): String {
        val authenticatedToken = token as? AuthenticatedToken
        val profile = authenticatedToken?.let { userService.fetchProfileSettings(it.userId) }
        val notifications = authenticatedToken?.let { userService.fetchNotificationsSettings(it.userId) }
        val hasAuthenticatedSettings = authenticatedToken != null

        return htmlRenderer.renderHtml(
            templatePath = "/templates/user_settings.html",
            specificTagResolvers = listOf(
                SimpleValueTagResolver(
                    "body_data_attrs",
                    dataAttr("authenticated", hasAuthenticatedSettings.toString()),
                ),
                authenticatedSettingsContentTagResolver(hasAuthenticatedSettings),
                SimpleValueTagResolver("profile_description", escapeHtml(profile?.description.orEmpty())),
                SimpleValueTagResolver("profile_country", escapeHtmlAttr(profile?.country.orEmpty())),
                SimpleValueTagResolver("show_pvp_games_checked", if (profile?.showPvpGamesOnProfile == true) "checked" else ""),
                SimpleValueTagResolver("show_pvb_games_checked", if (profile?.showPvbGamesOnProfile == true) "checked" else ""),
                SimpleValueTagResolver("page_views_retention_days", ARCHIVE_PAGE_VIEW_AFTER_DAYS.toString()),
                notificationsTableTagResolver(
                    notifications
                        ?: NotificationsSettingsDto(
                            newsletter = false,
                            opponentJoinedGame = false,
                            opponentPlayedMove = false,
                            opponentResigned = false,
                            opponentProposedDraw = false,
                            opponentAcceptedDraw = false,
                            opponentDeclinedDraw = false,
                        ),
                ),
            ),
        )
    }

    private fun authenticatedSettingsContentTagResolver(hasAuthenticatedSettings: Boolean): TagResolver =
        CallbackTagResolver("authenticated_user_settings_content") {
            if (hasAuthenticatedSettings) {
                WebFragmentResolver("authenticated_user_settings_content").resolveContent().firstOrNull() ?: ""
            } else {
                ""
            }
        }

    private fun notificationsTableTagResolver(dto: NotificationsSettingsDto): TagResolver {
        val rows = listOf(
            NotificationRow("newsletter", "Newsletter. At most once a month.", dto.newsletter),
            NotificationRow(
                "opponent-joined-game",
                "Somebody joined a game you created - but you're offline.",
                dto.opponentJoinedGame,
            ),
            NotificationRow(
                "opponent-played-move",
                "Your opponent played a move - but you're offline.",
                dto.opponentPlayedMove,
            ),
            NotificationRow("opponent-resigned", "Your opponent resigned - but you're offline.", dto.opponentResigned),
            NotificationRow(
                "opponent-proposed-draw",
                "Your opponent proposed a draw - but you're offline.",
                dto.opponentProposedDraw,
            ),
            NotificationRow(
                "opponent-accepted-draw",
                "Your opponent accepted a draw - but you're offline.",
                dto.opponentAcceptedDraw,
            ),
            NotificationRow(
                "opponent-declined-draw",
                "Your opponent declined a draw - but you're offline.",
                dto.opponentDeclinedDraw,
            ),
        )

        return KtorHtmlBuilderTagResolver("notifications_settings_table") {
            table {
                id = "notifications-settings-table"
                rows.forEach { row ->
                    tr {
                        td("checkbox-cell") {
                            div {
                                div {
                                    checkBoxInput {
                                        id = "${row.id}-checkbox"
                                        checked = row.checked
                                    }
                                }
                            }
                        }
                        td {
                            label("settings-hit-box") {
                                attributes["for"] = "${row.id}-checkbox"
                                div { span { +row.label } }
                            }
                        }
                    }
                }
            }
        }
    }

    private data class NotificationRow(
        val id: String,
        val label: String,
        val checked: Boolean,
    )

    private companion object {

        fun dataAttr(name: String, value: String?): String =
            if (value.isNullOrBlank()) "" else """ data-$name="${escapeHtmlAttr(value)}""""
    }
}
