package io.elephantchess.servicelayer.dto.user

data class GameStatsResponse(
    val ratings: TimeCategoryStatsDto,
    val pvp: TimeCategoryPlayerVsPlayerStatsDto,
)
