package io.elephantchess.servicelayer.dto.database

data class PlayerGameStatsResponse(
    val player: PlayerStats,
    val withDuplicates: PlayerStats?
) {
    data class PlayerStats(
        val redWins: Int,
        val redLosses: Int,
        val redDraws: Int,
        val blackWins: Int,
        val blackLosses: Int,
        val blackDraws: Int
    )
}
