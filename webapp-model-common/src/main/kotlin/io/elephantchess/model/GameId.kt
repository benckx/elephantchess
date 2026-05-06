package io.elephantchess.model

/**
 * id that identify either a PVP game, a game vs bot or a reference game
 */
data class GameId(val type: GameType, val id: String) {

    override fun toString(): String {
        return "Game[$type-$id]"
    }

}
