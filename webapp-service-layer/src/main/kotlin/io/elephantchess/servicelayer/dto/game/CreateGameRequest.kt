package io.elephantchess.servicelayer.dto.game

import io.elephantchess.model.TimeControlMode
import io.elephantchess.xiangqi.Color

data class CreateGameRequest(
    val inviterColor: Color?,
    val isRated: Boolean,
    val timeControlBase: Int,
    val timeControlIncrement: Int?,
    val timeControlMode: TimeControlMode,
    val allowGuests: Boolean,
    val alwaysVisibleInLobby: Boolean,
    val privateInvite: Boolean,
)
