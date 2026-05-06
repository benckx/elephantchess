package io.elephantchess.servicelayer.dto.sevenkingdoms

import io.elephantchess.sevenkingdoms.Color

data class JoinGameRequest(
    val gameId: String,
    val colors: List<Color>
)
