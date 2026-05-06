package io.elephantchess.db.model

import io.elephantchess.model.GameEventType

data class GamePlayersStatus(
    val inviterId: String,
    val inviteeId: String?,
    val status: GameEventType,
    val allowGuests: Boolean,
) {

    private fun allPlayers(): List<String> = listOfNotNull(inviterId, inviteeId)

    fun isInviter(userId: String) = inviterId == userId

    fun isInvitee(userId: String) = inviteeId == userId

    fun hasInvitee() = inviteeId != null

    fun getOpponentUserId(userId: String): String? {
        return when {
            isInviter(userId) -> inviteeId
            isInvitee(userId) -> inviterId
            else -> null
        }
    }

    fun isPlaying(userId: String): Boolean {
        return allPlayers().contains(userId)
    }

    fun isGameInProgress(): Boolean {
        return status.isInProgress()
    }

}
