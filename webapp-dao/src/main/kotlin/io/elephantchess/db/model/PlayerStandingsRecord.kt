package io.elephantchess.db.model

data class PlayerStandingsRecord(
    val playerId: String,
    val canonicalName: String,
    val chineseName: String?,
    val slug: String,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val totalGames: Int
)
