package io.elephantchess.servicelayer.dto.botgame

data class PlayMoveBotGameRequest(
    val gameId: String,
    val move: String,
)
