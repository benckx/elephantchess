package io.elephantchess.servicelayer.dto.global

data class PuzzleStatsLeaderboardResponse(val entries: List<Entry>) {

    data class Entry(
        val username: String,
        val countryCode: String?,
        val last: Int,
        val max: Int,
        val total: Int,
        val lastPlayed: Long,
        val solvedRate: Double,
        val failedRate: Double,
    )

}
