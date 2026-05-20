package io.elephantchess.servicelayer.dto.user

data class TimeCategoryPlayerVsPlayerStatsDto(
    val bullet: PlayerVsPlayerStatsDto,
    val blitz: PlayerVsPlayerStatsDto,
    val rapid: PlayerVsPlayerStatsDto,
    val classical: PlayerVsPlayerStatsDto,
    val severalDays: PlayerVsPlayerStatsDto,
    val correspondence: PlayerVsPlayerStatsDto,
)
