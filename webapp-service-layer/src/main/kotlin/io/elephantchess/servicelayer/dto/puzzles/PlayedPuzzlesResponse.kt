package io.elephantchess.servicelayer.dto.puzzles

import io.elephantchess.model.PuzzleCategory
import io.elephantchess.model.PuzzleOutcome
import io.elephantchess.xiangqi.Color

class PlayedPuzzlesResponse(val entries: List<Entry>) {

    data class Entry(
        val puzzleId: String,
        val playerColor: Color,
        val startFen: String,
        val categories: List<PuzzleCategory>,
        val outcome: PuzzleOutcome,
        val ratingFrom: Int,
        val ratingTo: Int,
        val date: Long,
    )

}
