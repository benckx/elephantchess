package io.elephantchess.servicelayer.dto.database

data class PlayersListResponse(val entries: List<Entry>) {

    data class Entry(
        val playerId: String,
        val name: String,
        val slug: String,
        val wins: Int,
        val draws: Int,
        val losses: Int,
        val totalGames: Int
    )

}