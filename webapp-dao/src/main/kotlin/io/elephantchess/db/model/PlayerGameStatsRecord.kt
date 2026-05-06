package io.elephantchess.db.model

data class PlayerGameStatsRecord(
    val redWins: Int,
    val redLosses: Int,
    val redDraws: Int,
    val blackWins: Int,
    val blackLosses: Int,
    val blackDraws: Int
)
