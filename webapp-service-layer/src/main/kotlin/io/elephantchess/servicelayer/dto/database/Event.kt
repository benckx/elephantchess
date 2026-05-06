package io.elephantchess.servicelayer.dto.database

import io.elephantchess.model.Outcome
import java.time.LocalDate

data class Event(
    val id: String,
    val name: String,
    val scores: Map<String, Int>,
    val games: List<Game>
) {

    /**
     * Maps playerId to (display name, slug) pair
     */
    val playerLookup: Map<String, Pair<String, String?>> by lazy {
        buildMap {
            for (game in games) {
                game.redPlayerId?.let { id ->
                    game.redPlayerName?.let { name ->
                        put(id, name to game.redPlayerSlug)
                    }
                }
                game.blackPlayerId?.let { id ->
                    game.blackPlayerName?.let { name ->
                        put(id, name to game.blackPlayerSlug)
                    }
                }
            }
        }
    }

    data class Game(
        val id: String,
        val redPlayerId: String?,
        val redPlayerName: String?,
        val redPlayerSlug: String?,
        val blackPlayerId: String?,
        val blackPlayerName: String?,
        val blackPlayerSlug: String?,
        val outcome: Outcome,
        val round: Int?,
        val date: LocalDate?,
        val finalFen: String
    )

}
