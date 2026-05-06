package io.elephantchess.servicelayer.dto.admin

data class PreRecordPuzzleSolutionStatsResponse(val entries: List<Entry>) {

    data class Entry(
        val userId: String,
        val username: String,
        val total: Int,
        val preRecordSolutionRate: Float,
    )

}
