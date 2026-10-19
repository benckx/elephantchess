package io.elephantchess.servicelayer.dto.puzzles

data class PuzzleStatsSummaryResponse(
    val rating: Int,
    val maxRating: Int,
    val totalPlayed: Int,
)
