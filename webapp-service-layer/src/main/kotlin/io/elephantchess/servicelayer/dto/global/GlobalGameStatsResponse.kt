package io.elephantchess.servicelayer.dto.global

data class GlobalGameStatsResponse(
    val totalGames: Int,
    val totalInAppGames: Int,
    // half moves
    val totalMoves : Int,
    // half moves
    val totalInAppMoves: Int,
    val totalManchuGames: Int,
)
