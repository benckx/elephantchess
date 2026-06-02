package io.elephantchess.db.model

import io.elephantchess.model.PuzzleOutcome
import io.elephantchess.xiangqi.Color
import kotlin.time.Instant

data class PlayedPuzzleRecord(
    val puzzleId: String,
    val color: Color,
    val startFen: String,
    val outcome: PuzzleOutcome,
    val ratingFrom: Int,
    val ratingTo: Int,
    val puzzleRatingFrom: Int,
    val puzzleRatingTo: Int,
    val date: Instant,
)
