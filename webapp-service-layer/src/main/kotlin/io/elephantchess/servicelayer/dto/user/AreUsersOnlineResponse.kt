package io.elephantchess.servicelayer.dto.user

data class AreUsersOnlineResponse(
    val onlineUserIds: Set<String>
)
