package io.elephantchess.servicelayer.dto.botgame

import io.elephantchess.model.Engine
import io.elephantchess.model.OpeningMode
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant

data class CreateBotGameRequest(
    val color: Color,
    val depth: Int,
    val engine: Engine,
    val startFen: String?,
    val openingMode: OpeningMode = OpeningMode.BY_FREQUENCY,
    val variant: Variant = Variant.XIANGQI,
)
