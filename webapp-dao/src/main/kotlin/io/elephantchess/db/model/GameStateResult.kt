package io.elephantchess.db.model

import io.elephantchess.model.GameEventType

data class GameStateResult(
    val fen: String,
    val index: Int,
    val gameEventType: GameEventType,
)
