package io.elephantchess.servicelayer.dto.game

import io.elephantchess.model.GameEventType
import io.elephantchess.model.TimeControlCategory
import io.elephantchess.model.UserOutcome
import io.elephantchess.model.UserType
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant

data class ListUserGamesResponse(val entries: List<Entry>) {

    data class Entry(
        val gameId: String,
        val status: GameEventType,
        val moveIndex: Int,
        val currentFen: String,
        val userHasToPlay: Boolean,
        val color: Color?,
        val isRated: Boolean,
        val timeControlCategory: TimeControlCategory,
        val timeControlBase: Int?,
        val timeControlIncrement: Int?,
        val opponentUserType: UserType?,
        val opponentUserId: String?,
        val opponentUsername: String?,
        val outcome: UserOutcome?,
        val ratingFrom: Int?,
        val ratingTo: Int?,
        val created: Long,
        val lastUpdated: Long,
        val numberOfMessages: Int,
        val variant: Variant,
    )

}
