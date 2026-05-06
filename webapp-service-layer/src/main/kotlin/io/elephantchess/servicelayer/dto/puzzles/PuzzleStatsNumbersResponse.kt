package io.elephantchess.servicelayer.dto.puzzles

data class PuzzleStatsNumbersResponse(val history: List<Entry>) {

    data class Entry(
        val date: String,
        val solved: Int,
        val skipped: Int,
        val failed: Int,
    ) {

        fun isOnlyZeros(): Boolean {
            return solved == 0 && skipped == 0 && failed == 0
        }

    }

}
