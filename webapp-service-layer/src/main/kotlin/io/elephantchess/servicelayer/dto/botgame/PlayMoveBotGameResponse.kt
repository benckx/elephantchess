package io.elephantchess.servicelayer.dto.botgame

import io.elephantchess.model.GameEventType
import io.elephantchess.model.Outcome

data class PlayMoveBotGameResponse(
    val fen: String,
    val position: Int,
    val botMove: String?,
    val statusUpdate: GameEventType?,
    val outcome: Outcome? = null,
)
