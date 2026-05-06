package io.elephantchess.db.model

data class NumberOfGamesRecord(
    val id: String,
    val sourceName: String,
    val canonicalName: String,
    val chineseName: String?,
    val gamesAsRed: Int,
    val gamesAsBlack: Int,
    val totalGames: Int
)
