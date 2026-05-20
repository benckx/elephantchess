package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.Engine
import io.elephantchess.model.GameEventType
import io.elephantchess.model.Outcome
import io.elephantchess.model.UserType
import io.elephantchess.xiangqi.Color

data class ListBotGamesResponse(
    val entries: List<Entry>,
) {

    data class Entry(
        val gameId: String,
        val userId: String?,
        val username: String?,
        val userType: UserType?,
        val color: Color,
        val engine: Engine,
        val depth: Int,
        val customStartFen: Boolean,
        val status: GameEventType,
        val outcome: Outcome?,
        val index: Int,
        val isPreAnalyzed: Boolean,
        val created: Long,
        val lastUpdated: Long,
    )

}
