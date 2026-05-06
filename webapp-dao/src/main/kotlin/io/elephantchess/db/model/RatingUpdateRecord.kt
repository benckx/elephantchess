package io.elephantchess.db.model

data class RatingUpdateRecord(
    val isRated: Boolean,
    val inviterRatingFrom: Int,
    val inviterRatingTo: Int?,
    val inviteeRatingFrom: Int,
    val inviteeRatingTo: Int?,
)
