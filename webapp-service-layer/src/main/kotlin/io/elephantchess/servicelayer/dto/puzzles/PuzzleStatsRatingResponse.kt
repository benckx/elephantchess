package io.elephantchess.servicelayer.dto.puzzles

data class PuzzleStatsRatingResponse(val history: List<Entry>) {

    data class Entry(
        val date: String,
        val last: Int,
        val max: Int,
    )

}
