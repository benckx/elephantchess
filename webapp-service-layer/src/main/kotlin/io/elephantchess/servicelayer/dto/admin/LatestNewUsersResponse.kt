package io.elephantchess.servicelayer.dto.admin

data class LatestNewUsersResponse(
    val latestNewGuest: Long?,
    val latestNewAuthenticatedUser: Long?
)

