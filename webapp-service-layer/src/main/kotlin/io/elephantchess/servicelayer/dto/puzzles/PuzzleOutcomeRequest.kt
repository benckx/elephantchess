package io.elephantchess.servicelayer.dto.puzzles

import io.elephantchess.model.PuzzleOutcome

data class PuzzleOutcomeRequest(
    val puzzleId: String,
    val outcome: PuzzleOutcome,
    val usedPreRecordedSolution: Boolean,
    val visibleCategories: Boolean,
)
