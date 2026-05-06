package io.elephantchess.servicelayer.dto.admin

data class LatestPuzzleActivityResponse(
    val latestPlayedPuzzle: Long?,
    val latestPuzzleVote: Long?
)
