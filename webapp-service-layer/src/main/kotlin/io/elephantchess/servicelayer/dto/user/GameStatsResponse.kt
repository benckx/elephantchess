package io.elephantchess.servicelayer.dto.user

data class GameStatsResponse(
    val ratings: RatingsPerTimeCategory,
    val pvp: NumberOfGamePerTimeCategory,
)

data class RatingsPerTimeCategory(
    val bullet: Int,
    val blitz: Int,
    val rapid: Int,
    val classical: Int,
    val severalDays: Int,
    val correspondence: Int,
)

data class NumberOfGamePerTimeCategory(
    val bullet: NumberOfOutcomes,
    val blitz: NumberOfOutcomes,
    val rapid: NumberOfOutcomes,
    val classical: NumberOfOutcomes,
    val severalDays: NumberOfOutcomes,
    val correspondence: NumberOfOutcomes,
)

data class NumberOfOutcomes(
    val wins: Int,
    val losses: Int,
    val draws: Int,
)
