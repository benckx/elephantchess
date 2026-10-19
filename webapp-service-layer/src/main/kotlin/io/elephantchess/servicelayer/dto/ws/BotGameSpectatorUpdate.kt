package io.elephantchess.servicelayer.dto.ws

import io.elephantchess.model.GameEventType
import io.elephantchess.model.Outcome

data class BotGameSpectatorUpdate(
    val moveIndex: Int,
    val newMoves: List<String>,
    val status: GameEventType,
    val outcome: Outcome?,
)
