package io.elephantchess.servicelayer.dto.game

data class PlayMoveRequest(
    val gameId: String,
    val move: String,
)
