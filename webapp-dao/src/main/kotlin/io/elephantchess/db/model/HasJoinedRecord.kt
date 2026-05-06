package io.elephantchess.db.model

import io.elephantchess.xiangqi.Color

data class HasJoinedRecord(
    val gameId: String,
    val invitee: String?,
    val inviterColor: Color?,
    val inviterRating: Int,
    val inviteeRating: Int?,
)
