package io.elephantchess.servicelayer.dto.sevenkingdoms

import io.elephantchess.sevenkingdoms.Color

data class CancelJoinGameResponse(
    val colors: List<Color>
)
