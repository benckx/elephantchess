package io.elephantchess.servicelayer.dto.ws

import io.elephantchess.model.GameEventType
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.ChatMessage
import io.elephantchess.servicelayer.dto.game.TimeRemaining
import io.elephantchess.xiangqi.Color

data class PlayerVsPlayerUpdate(
    val status: GameEventType? = null,
    val hasJoined: HasJoined? = null,
    val drawPropositionUser: String? = null,
    val newMove: NewMove? = null,
    val ratingUpdate: RatingUpdate? = null,
    val timeRemaining: TimeRemaining? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val typingUserIds: List<String> = emptyList(),
)

data class HasJoined(
    val inviteeId: String,
    val inviteeUsername: String,
    val inviteeRating: Int,
    val inviteeUserType: UserType,
    val inviterColor: Color?,
)

data class NewMove(
    val move: String,
    val updatedIndex: Int,
    val updatedFen: String,
)

data class RatingUpdate(
    val isRated: Boolean,
    val inviterRatingFrom: Int,
    val inviterRatingTo: Int?,
    val inviteeRatingFrom: Int,
    val inviteeRatingTo: Int?,
)
