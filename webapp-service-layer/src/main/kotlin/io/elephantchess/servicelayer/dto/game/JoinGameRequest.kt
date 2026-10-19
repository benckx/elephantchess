package io.elephantchess.servicelayer.dto.game

import io.elephantchess.model.GameJoinSource

// TODO: remove null default values and adapt unit tests
data class JoinGameRequest(
    val gameId: String,
    val source: GameJoinSource? = null,
    val sourceId: String? = null,
)
