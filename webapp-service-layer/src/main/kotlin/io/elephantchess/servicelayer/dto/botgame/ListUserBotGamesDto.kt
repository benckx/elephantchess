package io.elephantchess.servicelayer.dto.botgame

import io.elephantchess.model.Engine
import io.elephantchess.model.GameEventType
import io.elephantchess.model.Outcome
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant

data class ListUserBotGamesDto(val entries: List<Entry>) {

    data class Entry(
        val gameId: String,
        val color: Color,
        val engine: Engine,
        val depth: Int,
        val customStartFen: Boolean,
        val currentFen: String,
        val status: GameEventType,
        val outcome: Outcome?,
        val moveIndex: Int,
        val isPreAnalyzed: Boolean,
        val created: Long,
        val lastUpdated: Long,
        val variant: Variant,
    )

}
