package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.UserType

data class OnlineUsersResponse(val entries: List<Entry>) {

    data class Entry(
        val id: String,
        val username: String,
        val userType: UserType,
    )

}
