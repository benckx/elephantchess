package io.elephantchess.servicelayer.dto.global

data class GlobalPuzzleStatsResponse(
    val totalPuzzles: Int,
    val totalPuzzlesPlayed: Int,
    val puzzlesPlayedRatio10x: Float,
    val puzzlesPlayedRatio20x: Float
)
