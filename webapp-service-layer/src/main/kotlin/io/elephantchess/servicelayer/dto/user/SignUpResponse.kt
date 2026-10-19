package io.elephantchess.servicelayer.dto.user

data class SignUpResponse(
    val userId: String,
    val username: String,
    val token: String,
)
