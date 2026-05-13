package io.elephantchess.servicelayer.dto.user

data class SignUpRequest(
    val username: String,
    val email: String,
    val password: String,
    val guestUserId: String? = null,
)
