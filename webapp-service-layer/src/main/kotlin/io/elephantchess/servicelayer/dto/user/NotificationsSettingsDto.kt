package io.elephantchess.servicelayer.dto.user

data class NotificationsSettingsDto(
    val newsletter: Boolean,
    val opponentJoinedGame: Boolean,
    val opponentFlagged: Boolean,
    val opponentPlayedMove: Boolean,
    val opponentResigned: Boolean,
    val opponentProposedDraw: Boolean,
    val opponentAcceptedDraw: Boolean,
    val opponentDeclinedDraw: Boolean,
)
