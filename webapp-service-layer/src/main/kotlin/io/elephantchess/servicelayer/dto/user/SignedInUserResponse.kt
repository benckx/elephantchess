package io.elephantchess.servicelayer.dto.user

import io.elephantchess.model.UserRole

data class SignedInUserResponse(
    val userId: String,
    val username: String,
    val token: String,
    val roles: List<UserRole>
)
