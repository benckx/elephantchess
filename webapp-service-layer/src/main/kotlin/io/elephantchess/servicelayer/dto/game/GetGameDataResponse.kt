package io.elephantchess.servicelayer.dto.game

import io.elephantchess.model.*
import io.elephantchess.xiangqi.Color

// TODO: restructure the 2 users
data class GetGameDataResponse(
    val inviterId: String,
    val inviterUsername: String,
    val inviterRating: Int,
    val inviterUserType: UserType,
    val inviteeId: String?,
    val inviteeUsername: String?,
    val inviteeRating: Int?,
    val inviteeUserType: UserType?,
    val inviterColor: Color?,
    val created: Long,
    val isRated: Boolean,
    val timeControlCategory: TimeControlCategory,
    val timeControlBase: Int?,
    val timeControlIncrement: Int?,
    val timeControlMode: TimeControlMode,
    val allowGuestsToJoin: Boolean,
    val privateInvite: Boolean,
    val fen: String,
    val moveIndex: Int,
    val timeRemaining: TimeRemaining?,
    val gameEventType: GameEventType,
    val outcome: Outcome?,
    val ratingUpdate: RatingUpdate?,
    val drawPropositionUser: String?,
)
