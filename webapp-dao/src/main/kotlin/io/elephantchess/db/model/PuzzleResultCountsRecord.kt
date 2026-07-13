package io.elephantchess.db.model

data class PuzzleResultCountsRecord(
    val userId: String,
    val solved: Int,
    val failed: Int,
    val total: Int
)
