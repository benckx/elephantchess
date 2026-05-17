package io.elephantchess.webapp.rendering

import io.elephantchess.model.Outcome
import io.elephantchess.servicelayer.dto.database.Event
import io.elephantchess.servicelayer.dto.database.EventsListResponse
import io.elephantchess.servicelayer.dto.database.PlayersListResponse
import io.ktor.util.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

fun eventsTableTagResolver(
    eventsListResponse: EventsListResponse
) = CallbackTagResolver("events_table") {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    fun formatDate(dateString: String?): String {
        if (dateString == null) return "-"
        return try {
            LocalDate.parse(dateString).format(dateFormatter)
        } catch (_: Exception) {
            "-"
        }
    }

    buildString {
        append("""<table class="database-table events-list-table nice-table nice-table-striped">""")
        append("<thead><tr>")
        append("<th>Event</th>")
        append("<th>Date</th>")
        append("<th>Rounds</th>")
        append("<th>Games</th>")
        append("</tr></thead>")
        append("<tbody>")
        for (entry in eventsListResponse.entries) {
            append("<tr>")
            append("""<td><a href="/database/event?id=${entry.id}">${entry.name}</a></td>""")
            append("<td>${formatDate(entry.date)}</td>")
            append("<td>${entry.maxRound ?: "-"}</td>")
            append("<td>${entry.gameCount}</td>")
            append("</tr>")
        }
        append("</tbody>")
        append("</table>")
    }
}

fun playersTableTagResolver(
    playerStandings: PlayersListResponse
) = CallbackTagResolver("players_table") {
    buildString {
        append("""<table class="database-table standings-table nice-table nice-table-striped">""")
        append("<thead><tr>")
        append("<th>Player</th>")
        append("<th>W</th>")
        append("<th>D</th>")
        append("<th>L</th>")
        append("<th>Games</th>")
        append("</tr></thead>")
        append("<tbody>")
        for (entry in playerStandings.entries) {
            append("<tr>")
            append("""<td><a href="/database/player/${entry.slug}">${entry.name}</a></td>""")
            append("<td>${entry.wins}</td>")
            append("<td>${entry.draws}</td>")
            append("<td>${entry.losses}</td>")
            append("<td>${entry.totalGames}</td>")
            append("</tr>")
        }
        append("</tbody>")
        append("</table>")
    }
}

fun eventGamesListTagResolver(
    event: Event
) = CallbackTagResolver("event_games_list") {
    val eventId = event.id
    val games = event.games
    val scores = event.scores
    val playerLookup = event.playerLookup

    val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)
    fun formatDate(date: LocalDate) = date.format(dateFormatter)

    fun formatPlayerName(playerName: String?, playerSlug: String?): String {
        return if (playerName != null && playerSlug != null) {
            """<a href="/database/player/${playerSlug}">${playerName}</a>"""
        } else {
            playerName ?: "<unknown>".escapeHTML()
        }
    }

    fun formatOutcome(game: Event.Game): String {
        return when (game.outcome) {
            Outcome.RED_WINS -> "2-0"
            Outcome.BLACK_WINS -> "0-2"
            Outcome.DRAW -> "1-1"
        }
    }

    fun formatOutcomeAnchor(game: Event.Game): String {
        val colorClass = when (game.outcome) {
            Outcome.RED_WINS -> "red-color"
            Outcome.BLACK_WINS -> "black-color"
            Outcome.DRAW -> "any-color"
        }

        val url = """/database/game?id=${game.id}"""
        return """<a class="outcome-game-link $colorClass" href="$url">${formatOutcome(game)}</a>"""
    }

    fun renderGamesTable(games: List<Event.Game>): List<String> {
        if (games.isEmpty()) return emptyList()

        val sortedGames = games.sortedWith(
            compareBy({ game -> game.date }, { games -> games.id })
        )

        val lines = mutableListOf<String>()
        lines += """<div><table class="database-table game-round-table nice-table nice-table-striped">"""
        lines += "<tr><th>red</th><th>outcome</th><th>black</th><tr>"
        for (game in sortedGames) {
            lines += """<tr 
                        |class="game-row-hoverable" 
                        |data-fen="${game.finalFen}" 
                        |data-game-id="${game.id}">""".trimMargin()

            lines += "<td>${formatPlayerName(game.redPlayerName, game.redPlayerSlug)}</td>"
            lines += "<td>${formatOutcomeAnchor(game)}</td>"
            lines += "<td>${formatPlayerName(game.blackPlayerName, game.blackPlayerSlug)}</td>"
            lines += "</tr>"
        }
        lines += "</table></div>"
        return lines
    }

    fun renderRound(games: List<Event.Game>): String {
        if (games.isEmpty()) return ""

        val lines = mutableListOf<String>()

        // title
        val round = games.first().round?.toString() ?: "<unknown>".escapeHTML()
        lines += "<h2>Round ${round}</h2>"

        // dates
        val earliestDate = games.mapNotNull { it.date }.minOrNull()
        val latestDate = games.mapNotNull { it.date }.maxOrNull()
        if (earliestDate != null && latestDate != null) {
            lines += if (earliestDate == latestDate) {
                """<span class="round-date">${formatDate(earliestDate)}</span>"""
            } else {
                """<span class="round-date">${formatDate(earliestDate)} - ${formatDate(latestDate)}</span>"""
            }
        }

        // games
        lines += renderGamesTable(games)

        // browse links
        val roundNumber = games.first().round
        lines += """<div class="browse-links">"""
        if (roundNumber != null) {
            lines += """<a href="/browse/event?id=${eventId}&round=${roundNumber}">browse round $roundNumber games</a>"""
            lines += """ · """
        }
        lines += """<a href="/browse/event?id=${eventId}">browse all event games</a>"""
        lines += """</div>"""

        return lines.joinToString("\n")
    }

    fun renderScoresTable(): String {
        if (scores.isEmpty()) return ""

        // Calculate wins, draws, losses for each player
        data class PlayerStats(var wins: Int = 0, var draws: Int = 0, var losses: Int = 0)

        val playerStats = mutableMapOf<String, PlayerStats>()

        for (game in games) {
            val redId = game.redPlayerId
            val blackId = game.blackPlayerId

            if (redId != null) {
                val stats = playerStats.getOrPut(redId) { PlayerStats() }
                when (game.outcome) {
                    Outcome.RED_WINS -> stats.wins++
                    Outcome.BLACK_WINS -> stats.losses++
                    Outcome.DRAW -> stats.draws++
                }
            }
            if (blackId != null) {
                val stats = playerStats.getOrPut(blackId) { PlayerStats() }
                when (game.outcome) {
                    Outcome.RED_WINS -> stats.losses++
                    Outcome.BLACK_WINS -> stats.wins++
                    Outcome.DRAW -> stats.draws++
                }
            }
        }

        val sortedScores = scores
            .mapNotNull { (playerId, score) ->
                val (name, slug) = playerLookup[playerId] ?: return@mapNotNull null
                val stats = playerStats[playerId] ?: PlayerStats()
                Triple(playerId, name to slug, score) to stats
            }
            .sortedByDescending { (triple, _) -> triple.third }

        val lines = mutableListOf<String>()
        lines += """<h2>Standings</h2>"""
        lines += """<table class="database-table scores-table nice-table nice-table-striped">"""
        lines += "<tr><th>#</th><th>Player</th><th>W</th><th>D</th><th>L</th><th>Score</th></tr>"
        for ((index, entry) in sortedScores.withIndex()) {
            val (triple, stats) = entry
            val (_, nameAndSlug, score) = triple
            val (name, slug) = nameAndSlug
            lines += "<tr>"
            lines += "<td>${index + 1}</td>"
            lines += "<td>${formatPlayerName(name, slug)}</td>"
            lines += "<td>${stats.wins}</td>"
            lines += "<td>${stats.draws}</td>"
            lines += "<td>${stats.losses}</td>"
            lines += "<td>$score</td>"
            lines += "</tr>"
        }
        lines += "</table>"
        return lines.joinToString("\n")
    }

    val byRounds = games
        .groupBy { it.round }
        .toList()
        .sortedBy { (round, _) -> round }

    val scoresHtml = renderScoresTable()
    val gamesHtml = if (byRounds.size > 1) {
        val withRounds = byRounds.filter { (round, _) -> round != null }
        val withoutRounds = byRounds.filter { (round, _) -> round == null }

        (withRounds + withoutRounds)
            .joinToString("\n") { (_, games) -> renderRound(games) }
    } else {
        val tableHtml = renderGamesTable(games).joinToString("\n")
        val browseLink =
            """<div class="browse-links"><a href="/browse/event?id=${eventId}">browse all event games</a></div>"""
        "$tableHtml\n$browseLink"
    }

    listOf(scoresHtml, gamesHtml).filter { it.isNotEmpty() }.joinToString("\n")
}
