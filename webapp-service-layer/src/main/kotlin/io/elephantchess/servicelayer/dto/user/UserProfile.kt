package io.elephantchess.servicelayer.dto.user

data class UserProfile(
    val userId: String,
    val username: String,
    val country: String?,
    val profileDescription: String?,
    val puzzleRating: Int,
)
