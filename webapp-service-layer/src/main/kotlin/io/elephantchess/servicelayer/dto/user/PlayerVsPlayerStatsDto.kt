package io.elephantchess.servicelayer.dto.user

data class PlayerVsPlayerStatsDto(
    val wins: Int,
    val losses: Int,
    val draws: Int,
)
