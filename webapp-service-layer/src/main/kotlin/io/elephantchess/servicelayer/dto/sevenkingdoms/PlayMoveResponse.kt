package io.elephantchess.servicelayer.dto.sevenkingdoms

import io.elephantchess.model.GameEventType
import io.elephantchess.sevenkingdoms.ArmyCapturedEvent
import io.elephantchess.sevenkingdoms.Color
import io.elephantchess.sevenkingdoms.VictoryType

data class PlayMoveResponse(
    val index: Int,
    val colorToPlay: Color?,
    val statusUpdate: GameEventType?,
    val winner: Color?,
    val victoryType: VictoryType?,
    val armyCapturedEvent : ArmyCapturedEvent?
)
