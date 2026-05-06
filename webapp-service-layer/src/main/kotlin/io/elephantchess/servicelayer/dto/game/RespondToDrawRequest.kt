package io.elephantchess.servicelayer.dto.game

data class RespondToDrawRequest(
    val gameId: String,
    val accept: Boolean
)
