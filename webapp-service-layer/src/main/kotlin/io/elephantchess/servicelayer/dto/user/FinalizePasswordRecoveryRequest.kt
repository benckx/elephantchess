package io.elephantchess.servicelayer.dto.user

data class FinalizePasswordRecoveryRequest(
    val email: String,
    val code: String,
    val newPassword: String,
)
