package io.elephantchess.servicelayer.dto.botgame

import io.elephantchess.model.Engine
import io.elephantchess.model.GameEventType
import io.elephantchess.model.Outcome
import io.elephantchess.model.UserType
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant

data class GetBotGameDataResponse(
    val userId: String?,
    val username: String?,
    val userType: UserType?,
    val userColor: Color,
    val engine: Engine,
    val depth: Int,
    val startFen: String,
    val status: GameEventType,
    val moveIndex: Int,
    val fen: String,
    val created: Long,
    val lastUpdated: Long,
    val outcome: Outcome?,
    val variant: Variant = Variant.XIANGQI,
)
