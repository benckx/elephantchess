package io.elephantchess.servicelayer.dto.botgame

import io.elephantchess.model.Engine
import io.elephantchess.xiangqi.Color

data class CreateBotGameRequest(
    val color: Color,
    val depth: Int,
    val engine: Engine,
    val startFen: String?,
    val randomizeOpening: Boolean = false,
)
