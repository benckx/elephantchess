package io.elephantchess.servicelayer.dto.user

data class ProfileSettingsDto(
    val description: String,
    val country: String,
    val profilePictureUrl: String? = null,
)
