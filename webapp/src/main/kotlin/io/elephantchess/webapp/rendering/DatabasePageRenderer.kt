package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.HtmlRenderer
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.model.Outcome
import io.elephantchess.servicelayer.dto.database.*
import io.elephantchess.utils.cropToFirstNWords
import io.elephantchess.utils.formatWithChineseName
import io.ktor.http.encodeURLPath
import io.ktor.http.encodeURLQueryComponent
import io.github.reactivecircus.cache4k.Cache
import kotlin.time.Duration.Companion.hours

class DatabasePageRenderer(private val htmlRenderer: HtmlRenderer) {

    private val eventPagesCache =
        Cache
            .Builder<String, String>()
            .expireAfterWrite(1.hours)
            .build()

    private val pageOfListCache =
        Cache
            .Builder<String, String>()
            .expireAfterWrite(6.hours)
            .build()

    suspend fun renderPlayerPage(
        databasePlayer: DatabasePlayer,
        requestedVersion: Int?,
        edit: DatabasePlayerProfileEdit,
        fetchEditorsUsername: suspend () -> List<String>,
    ): String {
        val description = edit.profileText

        val playerNameEncodedResolver = SimpleValueTagResolver("player_name_encoded", databasePlayer.urlName)
        val playerIdResolver = SimpleValueTagResolver("player_id", databasePlayer.id)

        val descriptionResolver = CallbackTagResolver("player_profile_description") {
            description?.let { formatNewLinesToHtmlParagraphs(it) }
        }

        val sourcesResolver = CallbackTagResolver("player_profile_sources") {
            edit.sources
                .sortedBy { it.index }
                .joinToString("") { source ->
                    """<li><a href="${source.url}" target="_blank" rel="noopener noreferrer">${source.title}</a></li>"""
                }
        }

        val styleResolver = CallbackTagResolver("player_profile_description_style") {
            if (description == null) {
                "<style>#player-profile-description { display: none; }</style>"
            } else {
                ""
            }
        }

        val authorMeta = CallbackTagResolver("author_meta") {
            val editors = fetchEditorsUsername()
            if (editors.isNotEmpty()) {
                meta("author", editors.sorted().joinToString(", "))
            } else {
                ""
            }
        }

        val noIndexMeta = CallbackTagResolver("meta_no_index_conditional") {
            if (!edit.enabled || requestedVersion != null) {
                WebFragmentResolver("meta_no_index").resolveContent().firstOrNull() ?: ""
            } else {
                ""
            }
        }

        return htmlRenderer.renderHtml(
            templatePath = "/templates/database/database_player.html",
            specificTagResolvers = listOf(
                databasePlayerTitle(databasePlayer),
                descriptionMeta(edit),
                playerNameEncodedResolver,
                playerIdResolver,
                descriptionResolver,
                sourcesResolver,
                styleResolver,
                authorMeta,
                noIndexMeta
            ),
            canonicalPath = "/database/player/${databasePlayer.urlName}"
        )
    }

    suspend fun renderPlayerEditPage(databasePlayer: DatabasePlayer): String {
        val playerIdResolver = SimpleValueTagResolver("player_id", databasePlayer.id)

        return htmlRenderer.renderHtml(
            templatePath = "/templates/database/database_player_edit.html",
            specificTagResolvers = listOf(
                playerIdResolver
            ),
            canonicalPath = "/database/player/${databasePlayer.urlName}/edit"
        )
    }

    suspend fun renderEditHistoryPage(databasePlayer: DatabasePlayer): String {
        val playerIdResolver = SimpleValueTagResolver("player_id", databasePlayer.id)

        return htmlRenderer.renderHtml(
            templatePath = "/templates/database/database_player_edit_history.html",
            specificTagResolvers = listOf(
                playerIdResolver
            ),
            canonicalPath = "/database/player/${databasePlayer.urlName}/edit-history"
        )
    }

    suspend fun renderPlayerPageDiff(
        databasePlayer: DatabasePlayer,
        fromVersion: Int,
        toVersion: Int,
        fetchEdit: suspend (Int) -> DatabasePlayerProfileEdit
    ): String {
        fun mapGender(gender: String?): String {
            return when (gender) {
                "M" -> "Male"
                "F" -> "Female"
                else -> ""
            }
        }

        val editFrom = fetchEdit(fromVersion)
        val editTo = fetchEdit(toVersion)

        val editCommentResolver =
            SimpleValueTagResolver("edit_comment", editTo.editComment ?: "")

        // Chinese name
        val chineseNameFromResolver =
            SimpleValueTagResolver("chinese_name_from", editFrom.chineseName ?: "")
        val chineseNameToResolver =
            SimpleValueTagResolver("chinese_name_to", editTo.chineseName ?: "")

        // gender
        val genderFromResolver =
            SimpleValueTagResolver("gender_from", mapGender(editFrom.gender))
        val genderToResolver =
            SimpleValueTagResolver("gender_to", mapGender(editTo.gender))

        // description
        val descriptionFromResolver =
            SimpleValueTagResolver("player_profile_description_from", editFrom.profileText ?: "")
        val descriptionToResolver =
            SimpleValueTagResolver("player_profile_description_to", editTo.profileText ?: "")

        // sources
        val sourcesFromResolver =
            CallbackTagResolver("player_profile_sources_from") { formatSourcesToMarkdown(editFrom.sources) }
        val sourcesToResolver =
            CallbackTagResolver("player_profile_sources_to") { formatSourcesToMarkdown(editTo.sources) }

        return htmlRenderer.renderHtml(
            templatePath = "/templates/database/database_player_edit_diff.html",
            specificTagResolvers = listOf(
                databasePlayerTitle(databasePlayer, " Edit Diff"),
                SimpleValueTagResolver("player_id", databasePlayer.id),
                editCommentResolver,
                chineseNameFromResolver,
                chineseNameToResolver,
                genderFromResolver,
                genderToResolver,
                descriptionFromResolver,
                descriptionToResolver,
                sourcesFromResolver,
                sourcesToResolver
            )
        )
    }

    suspend fun renderBrowseGamesPage(
        databasePlayer: DatabasePlayer,
    ): String {
        val playerNameResolver = SimpleValueTagResolver("player_name", databasePlayer.canonicalName)
        return htmlRenderer.renderHtml(
            templatePath = "/templates/database/browse_db_player_games.html",
            specificTagResolvers = listOf(
                databasePlayerTitle(databasePlayer),
                playerNameResolver
            ),
            canonicalPath = "/database/player/${databasePlayer.urlName}/games"
        )
    }

    suspend fun renderBrowseEventGamesPage(
        eventId: String,
        eventName: String,
        round: Int? = null,
    ): String {
        val canonicalPath = buildString {
            append("/browse/event?id=")
            append(eventId)
            if (round != null) {
                append("&round=")
                append(round)
            }
        }
        return htmlRenderer.renderHtml(
            templatePath = "/templates/database/browse_db_event_games.html",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("page_title", eventName),
                SimpleValueTagResolver("event_id", eventId)
            ),
            canonicalPath = canonicalPath
        )
    }

    suspend fun renderEventPage(event: Event): String {
        return eventPagesCache.get(event.id) {
            // get top 10 players by score
            val topPlayerNames = event.scores
                .entries
                .sortedByDescending { entry -> entry.value }
                .take(10)
                .mapNotNull { entry -> event.playerLookup[entry.key]?.first }

            val description =
                "${event.name} with ${topPlayerNames.joinToString(", ")} - games, results and statistics."

            htmlRenderer.renderHtml(
                templatePath = "/templates/database/database_event.html",
                specificTagResolvers = listOf(
                    SimpleValueTagResolver("page_title", event.name),
                    SimpleValueTagResolver("description_meta", descriptionMeta(description)),
                    eventGamesListTagResolver(event)
                ),
                canonicalPath = "/database/event?id=${event.id}"
            )
        }
    }

    suspend fun renderEventsListPage(eventsList: EventsListResponse): String {
        return pageOfListCache.get("events") {
            htmlRenderer.renderHtml(
                templatePath = "/templates/database/database_events_list.html",
                specificTagResolvers = listOf(eventsTableTagResolver(eventsList)),
                canonicalPath = "/database/events"
            )
        }
    }

    suspend fun renderPlayersListPage(playersList: PlayersListResponse): String {
        return pageOfListCache.get("players") {
            htmlRenderer.renderHtml(
                templatePath = "/templates/database/database_players_list.html",
                specificTagResolvers = listOf(playersTableTagResolver(playersList)),
                canonicalPath = "/database/players"
            )
        }
    }

    suspend fun renderGamePage(summary: DatabaseGameSummary): String {
        val unknown = "<unknown>"
        val redCanonical = summary.redPlayerCanonicalName
        val blackCanonical = summary.blackPlayerCanonicalName
        val redDisplay =
            redCanonical?.let { canonicalName -> formatWithChineseName(canonicalName, summary.redPlayerChineseName) } ?: unknown
        val blackDisplay =
            blackCanonical?.let { canonicalName -> formatWithChineseName(canonicalName, summary.blackPlayerChineseName) } ?: unknown

        val title = "$redDisplay vs. $blackDisplay"

        val description = buildString {
            append("Chinese chess (Xiangqi) game between $redDisplay (red) and $blackDisplay (black)")
            if (!summary.eventName.isNullOrBlank()) append(" at ${summary.eventName}")
            summary.date?.let { append(", played on $it") }
            append(".")
        }

        fun playerLink(canonicalName: String?, displayName: String): String {
            return if (canonicalName.isNullOrBlank()) {
                escapeHtml(displayName)
            } else {
                val urlName = canonicalName.replace(" ", "_").encodeURLPath()
                """<a href="/database/player/$urlName">${escapeHtml(displayName)}</a>"""
            }
        }

        val playersInfoHtml = buildString {
            append("""<div class="player red">${playerLink(redCanonical, redDisplay)}</div>""")
            append("""<div class="player black">${playerLink(blackCanonical, blackDisplay)}</div>""")
        }

        val dateInfoHtml = summary.date?.toString() ?: ""

        val eventInfoHtml = summary.eventName?.let { name ->
            val escapedName = escapeHtml(name)
            val eventId = summary.eventId
            if (!eventId.isNullOrBlank()) {
                """<a href="/database/event?id=${eventId.encodeURLQueryComponent()}">$escapedName</a>"""
            } else {
                escapedName
            }
        } ?: ""

        val statusInfoHtml = when (summary.outcome) {
            Outcome.RED_WINS ->
                if (!redCanonical.isNullOrBlank()) "${escapeHtml(redDisplay)} victory (Red)" else "Red wins"
            Outcome.BLACK_WINS ->
                if (!blackCanonical.isNullOrBlank()) "${escapeHtml(blackDisplay)} victory (Black)" else "Black wins"
            Outcome.DRAW -> "Draw"
            null -> "--"
        }

        return htmlRenderer.renderHtml(
            templatePath = "/templates/database/database_game_viewer.html",
            specificTagResolvers = listOf(
                SimpleValueTagResolver("page_title", title),
                SimpleValueTagResolver("description_meta", descriptionMeta(description)),
                SimpleValueTagResolver("players_info", playersInfoHtml),
                SimpleValueTagResolver("game_status_info", statusInfoHtml),
                SimpleValueTagResolver("game_date_info", dateInfoHtml),
                SimpleValueTagResolver("game_event_info", eventInfoHtml),
            ),
            canonicalPath = "/database/game?id=${summary.gameId}"
        )
    }

    private companion object {

        fun descriptionMeta(edit: DatabasePlayerProfileEdit): TagResolver {
            return CallbackTagResolver("description_meta") {
                val profileText = edit.profileText
                val metaContent = if (profileText.isNullOrBlank()) {
                    val name = formatWithChineseName(edit.canonicalName, edit.chineseName)
                    "Chinese chess player $name games, statistics and openings."
                } else {
                    cropToFirstNWords(profileText, 100)
                }

                descriptionMeta(metaContent)
            }
        }

        fun databasePlayerTitle(databasePlayer: DatabasePlayer, suffix: String = ""): TagResolver {
            return SimpleValueTagResolver(
                "page_title",
                formatWithChineseName(databasePlayer.canonicalName, databasePlayer.chineseName) + suffix
            )
        }

        fun formatSourcesToMarkdown(sources: List<DatabasePlayerProfileSource>): String {
            return sources
                .sortedBy { it.index }
                .joinToString("\n") { source ->
                    "[${source.title}](${source.url})"
                }
        }

    }

}
