package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameJoinSource

data class ListGamesResponse(val entries: List<Entry>) {

    data class Entry(
        val gameId: String,
        val inviterUserId: String,
        val inviterUsername: String,
        val inviteeUserId: String?,
        val inviteeUsername: String?,
        val isRated: Boolean,
        val allowGuests: Boolean,
        val alwaysVisibleInLobby: Boolean,
        val privateInvite: Boolean,
        val timeControlBase: Int?,
        val timeControlIncrement: Int?,
        val status: GameEventType,
        val index: Int,
        val winnerUserId: String?,
        val created: Long,
        val lastUpdated: Long,
        val sourceType: GameJoinSource?
    )

}
