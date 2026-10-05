package io.elephantchess.webapp.rendering

import io.elephantchess.utils.di.KoinSingleton

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.KtorHtmlBuilderTagResolver
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.servicelayer.dto.user.NotificationsSettingsDto
import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.servicelayer.services.analytics.ARCHIVE_PAGE_VIEW_AFTER_DAYS
import kotlinx.html.checkBoxInput
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr

@KoinSingleton(eager = true)
class UserSettingsPageRenderer(
    private val htmlRenderer: HtmlRenderer,
    private val userService: UserService,
) {

    suspend fun renderUserSettingsPage(userId: String): String {
        val profile = userService.fetchProfileSettings(userId)
        val notifications = userService.fetchNotificationsSettings(userId)

        return htmlRenderer.renderHtml(
            templatePath = "/templates/user_settings.html",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("profile_description", escapeHtml(profile.description)),
                SimpleValueTagResolver("profile_country", escapeHtmlAttr(profile.country)),
                SimpleValueTagResolver("show_pvp_games_checked", if (profile.showPvpGamesOnProfile) "checked" else ""),
                SimpleValueTagResolver("show_pvb_games_checked", if (profile.showPvbGamesOnProfile) "checked" else ""),
                SimpleValueTagResolver("page_views_retention_days", ARCHIVE_PAGE_VIEW_AFTER_DAYS.toString()),
                notificationsTableTagResolver(notifications),
            )
        )
    }

    private fun notificationsTableTagResolver(dto: NotificationsSettingsDto): TagResolver {
        val rows = listOf(
            NotificationRow("newsletter", "Newsletter. At most once a month.", dto.newsletter),
            NotificationRow(
                "opponent-joined-game",
                "Somebody joined a game you created - but you're offline.",
                dto.opponentJoinedGame
            ),
            NotificationRow(
                "opponent-played-move",
                "Your opponent played a move - but you're offline.",
                dto.opponentPlayedMove
            ),
            NotificationRow("opponent-resigned", "Your opponent resigned - but you're offline.", dto.opponentResigned),
            NotificationRow(
                "opponent-proposed-draw",
                "Your opponent proposed a draw - but you're offline.",
                dto.opponentProposedDraw
            ),
            NotificationRow(
                "opponent-accepted-draw",
                "Your opponent accepted a draw - but you're offline.",
                dto.opponentAcceptedDraw
            ),
            NotificationRow(
                "opponent-declined-draw",
                "Your opponent declined a draw - but you're offline.",
                dto.opponentDeclinedDraw
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
        val checked: Boolean
    )

}
