package io.elephantchess.servicelayer.dto.user

data class EmailAddressSettingsResponse(
    val email: String,
    val validityStatus: EmailValidityStatus,
    val canResendConfirmation: Boolean,
)
