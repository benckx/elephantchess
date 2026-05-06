package io.elephantchess.servicelayer.dto.ws

import io.elephantchess.model.TimeControlCategory
import io.elephantchess.model.UserType
import io.elephantchess.xiangqi.Color

/**
 * To join or where it's user's turn to play
 */
data class GameToPlay(
    val gameId: String,
    val isRated: Boolean,
    val opponentUserId: String,
    val opponentUserType: UserType?,
    val opponentUsername: String,
    val opponentColor: Color?,
    val opponentRating: Int,
    val isOpponentOnline: Boolean,
    val timeControlCategory: TimeControlCategory,
    val timeControlBase: Int?,
    val timeControlIncrement: Int?,
    val allowGuests: Boolean,
    val lastUpdated: Long
)
