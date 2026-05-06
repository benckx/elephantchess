package io.elephantchess.servicelayer.dto.game

import io.elephantchess.model.GameEventType

data class PlayMoveResponse(
    val move: String,
    val updatedIndex: Int,
    val updatedFen: String,
    val gameEventType: GameEventType?,
    val ratingUpdate: RatingUpdate?
)
