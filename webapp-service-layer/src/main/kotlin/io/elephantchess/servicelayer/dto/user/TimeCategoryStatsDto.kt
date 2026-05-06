package io.elephantchess.servicelayer.dto.user

data class TimeCategoryStatsDto(
    val bullet: Int,
    val blitz: Int,
    val rapid: Int,
    val classical: Int,
    val severalDays: Int,
    val correspondence: Int,
)
