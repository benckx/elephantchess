package io.elephantchess.servicelayer.dto.puzzles

data class PuzzleVoteRequest(
    val puzzleId: String,
    val upVoted: Boolean,
)
