package io.elechantchess.sevenkingdoms.testutils

import io.elephantchess.sevenkingdoms.Board
import io.elephantchess.sevenkingdoms.Color
import io.elephantchess.sevenkingdoms.VictoryType

data class GameEntryDto(
    val gameId: String,
    val winner: Color,
    val victoryType: VictoryType,
    val capturedKingdoms: Map<Color, List<Color>>,
    val colorsStillInGame: List<Color>,
    val moves: List<String>,
) {

    fun toBoard(): Board {
        val board = Board()
        moves.forEach { board.registerMove(it) }
        return board
    }

}
