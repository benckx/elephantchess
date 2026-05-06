package io.elephantchess.servicelayer.dto.botgame

import io.elephantchess.model.GameEventType

data class PlayMoveBotGameResponse(
    val fen: String,
    val position: Int,
    val botMove: String?,
    val statusUpdate: GameEventType?,
)
