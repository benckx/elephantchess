package io.elephantchess.servicelayer.dto.lobby

import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameId
import io.elephantchess.model.Outcome

data class LatestGamesUpdateResponse(val entries: List<Entry>) {

    data class Entry(
        val gameId: GameId,
        val status: GameEventType,
        val fen: String,
        val lastUpdated: Long,
        val outcome: Outcome? = null,
        val isRedOnline: Boolean = false,
        val isBlackOnline: Boolean = false,
    )

}
