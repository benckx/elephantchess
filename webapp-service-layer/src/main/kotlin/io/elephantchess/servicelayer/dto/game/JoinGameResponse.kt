package io.elephantchess.servicelayer.dto.game

import io.elephantchess.xiangqi.Color

data class JoinGameResponse(
    val inviteeColor: Color,
    val inviteeRating: Int,
)
