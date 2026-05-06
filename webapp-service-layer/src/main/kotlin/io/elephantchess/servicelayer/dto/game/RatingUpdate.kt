package io.elephantchess.servicelayer.dto.game

/**
 * The change of Elo that resulted from a rated game
 */
data class RatingUpdate(
    val isRated: Boolean,
    val inviterRatingFrom: Int,
    val inviterRatingTo: Int?,
    val inviteeRatingFrom: Int,
    val inviteeRatingTo: Int?,
)
