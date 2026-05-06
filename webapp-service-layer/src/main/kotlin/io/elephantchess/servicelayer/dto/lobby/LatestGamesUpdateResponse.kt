package io.elephantchess.servicelayer.dto.lobby

import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameId

data class LatestGamesUpdateResponse(val entries: List<Entry>) {

    data class Entry(
        val gameId: GameId,
        val status: GameEventType,
        val fen: String,
        val lastUpdated : Long
    )

}
