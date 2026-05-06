package io.elephantchess.servicelayer.dto.game

import io.elephantchess.model.GameEventType
import io.elephantchess.xiangqi.Color

data class CreateGameResponse(
    val gameId: String,
    val eventType: GameEventType,
    val color: Color?
)
