package io.elephantchess.servicelayer.dto.sevenkingdoms

data class PlayMoveRequest(
    val gameId: String,
    val move: String
)
