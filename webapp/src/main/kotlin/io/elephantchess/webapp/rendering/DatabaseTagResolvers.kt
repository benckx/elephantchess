package io.elephantchess.webapp.rendering

import io.elephantchess.htmlrenderer.KtorHtmlBuilderTagResolver
import io.elephantchess.model.Outcome
import io.elephantchess.servicelayer.dto.database.Event
import io.elephantchess.servicelayer.dto.database.EventsListResponse
import io.elephantchess.servicelayer.dto.database.PlayersListResponse
import kotlinx.html.FlowContent
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

fun eventsTableTagResolver(
    eventsListResponse: EventsListResponse
) = KtorHtmlBuilderTagResolver("events_table") {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    fun formatDate(dateString: String?): String {
        if (dateString == null) return "-"
        return try {
            LocalDate.parse(dateString).format(dateFormatter)
        } catch (_: Exception) {
            "-"
        }
    }

    table(classes = "database-table events-list-table nice-table nice-table-striped") {
        thead {
            tr {
                th { +"Event" }
                th { +"Date" }
                th { +"Rounds" }
                th { +"Games" }
            }
        }
        tbody {
            for (entry in eventsListResponse.entries) {
                tr {
                    td { a(href = "/database/event?id=${entry.id}") { +entry.name } }
                    td { +formatDate(entry.date) }
                    td { +(entry.maxRound?.toString() ?: "-") }
                    td { +entry.gameCount.toString() }
                }
            }
        }
    }
}

fun playersTableTagResolver(
    playerStandings: PlayersListResponse
) = KtorHtmlBuilderTagResolver("players_table") {
    table(classes = "database-table standings-table nice-table nice-table-striped") {
        thead {
            tr {
                th { +"Player" }
                th { +"W" }
                th { +"D" }
                th { +"L" }
                th { +"Games" }
            }
        }
        tbody {
            for (entry in playerStandings.entries) {
                tr {
                    td { a(href = "/database/player/${entry.slug}") { +entry.name } }
                    td { +entry.wins.toString() }
                    td { +entry.draws.toString() }
                    td { +entry.losses.toString() }
                    td { +entry.totalGames.toString() }
                }
            }
        }
    }
}

fun eventGamesListTagResolver(
    event: Event
) = KtorHtmlBuilderTagResolver("event_games_list") {
    val eventId = event.id
    val games = event.games
    val scores = event.scores
    val playerLookup = event.playerLookup

    val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)
    fun formatDate(date: LocalDate) = date.format(dateFormatter)

    fun FlowContent.playerNameCell(playerName: String?, playerSlug: String?) {
        if (playerName != null && playerSlug != null) {
            a(href = "/database/player/$playerSlug") { +playerName }
        } else {
            +(playerName ?: "<unknown>")
        }
    }

    fun FlowContent.outcomeAnchor(game: Event.Game) {
        val colorClass = when (game.outcome) {
            Outcome.RED_WINS -> "red-color"
            Outcome.BLACK_WINS -> "black-color"
            Outcome.DRAW -> "any-color"
        }
        val outcomeText = when (game.outcome) {
            Outcome.RED_WINS -> "2-0"
            Outcome.BLACK_WINS -> "0-2"
            Outcome.DRAW -> "1-1"
        }
        a(classes = "outcome-game-link $colorClass", href = "/database/game?id=${game.id}") {
            +outcomeText
        }
    }

    fun TagConsumer<*>.emitGamesTable(gamesToRender: List<Event.Game>) {
        if (gamesToRender.isEmpty()) return
        val sortedGames = gamesToRender.sortedWith(compareBy({ it.date }, { it.id }))
        div {
            table(classes = "database-table game-round-table nice-table nice-table-striped") {
                tr {
                    th { +"red" }
                    th { +"outcome" }
                    th { +"black" }
                }
                for (game in sortedGames) {
                    tr(classes = "game-row-hoverable") {
                        attributes["data-fen"] = game.finalFen
                        attributes["data-game-id"] = game.id
                        td { playerNameCell(game.redPlayerName, game.redPlayerSlug) }
                        td { outcomeAnchor(game) }
                        td { playerNameCell(game.blackPlayerName, game.blackPlayerSlug) }
                    }
                }
            }
        }
    }

    // Scores / standings section
    if (scores.isNotEmpty()) {
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

        h2 { +"Standings" }
        table(classes = "database-table scores-table nice-table nice-table-striped") {
            tr {
                th { +"#" }
                th { +"Player" }
                th { +"W" }
                th { +"D" }
                th { +"L" }
                th { +"Score" }
            }
            for ((index, entry) in sortedScores.withIndex()) {
                val (triple, stats) = entry
                val (_, nameAndSlug, score) = triple
                val (name, slug) = nameAndSlug
                tr {
                    td { +(index + 1).toString() }
                    td { playerNameCell(name, slug) }
                    td { +stats.wins.toString() }
                    td { +stats.draws.toString() }
                    td { +stats.losses.toString() }
                    td { +score.toString() }
                }
            }
        }
    }

    // Games section, grouped by round when applicable
    val byRounds = games
        .groupBy { it.round }
        .toList()
        .sortedBy { (round, _) -> round }

    if (byRounds.size > 1) {
        val withRounds = byRounds.filter { (round, _) -> round != null }
        val withoutRounds = byRounds.filter { (round, _) -> round == null }

        for ((_, roundGames) in withRounds + withoutRounds) {
            if (roundGames.isEmpty()) continue

            val round = roundGames.first().round?.toString() ?: "<unknown>"
            h2 { +"Round $round" }

            val earliestDate = roundGames.mapNotNull { it.date }.minOrNull()
            val latestDate = roundGames.mapNotNull { it.date }.maxOrNull()
            if (earliestDate != null && latestDate != null) {
                span(classes = "round-date") {
                    if (earliestDate == latestDate) {
                        +formatDate(earliestDate)
                    } else {
                        +"${formatDate(earliestDate)} - ${formatDate(latestDate)}"
                    }
                }
            }

            emitGamesTable(roundGames)

            val roundNumber = roundGames.first().round
            div(classes = "browse-links") {
                if (roundNumber != null) {
                    a(href = "/browse/event?id=$eventId&round=$roundNumber") { +"browse round $roundNumber games" }
                    +" · "
                }
                a(href = "/browse/event?id=$eventId") { +"browse all event games" }
            }
        }
    } else {
        emitGamesTable(games)
        div(classes = "browse-links") {
            a(href = "/browse/event?id=$eventId") { +"browse all event games" }
        }
    }
}
