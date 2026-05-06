package io.elephantchess.servicelayer.dto.sevenkingdoms

import io.elephantchess.model.GameEventType
import io.elephantchess.sevenkingdoms.Color

data class JoinGameResponse(
    val colors: List<Color>,
    val status: GameEventType
)
