package io.elephantchess.servicelayer.dto.user

data class UserProfileDto(
    val userId: String,
    val username: String,
    val country: String?,
    val profileDescription: String?,
    val puzzleRating: Int,
    val showPvpGamesOnProfile: Boolean,
    val showPvbGamesOnProfile: Boolean,
)
