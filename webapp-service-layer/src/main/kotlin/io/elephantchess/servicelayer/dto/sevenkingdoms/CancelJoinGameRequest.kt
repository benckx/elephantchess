package io.elephantchess.servicelayer.dto.sevenkingdoms

import io.elephantchess.sevenkingdoms.Color

data class CancelJoinGameRequest(
    val gameId: String,
    val colors: List<Color>
)
