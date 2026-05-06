package io.elephantchess.db.callback

data class UpdateRatingsCallbackResult(
    val inviterNewRating: Int,
    val inviteeNewRating: Int
)
