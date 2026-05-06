package io.elephantchess.servicelayer.dto.puzzles

import io.elephantchess.model.PuzzleCategory
import io.elephantchess.xiangqi.Color

data class PuzzleResponse(
    val id: String,
    val fen: String,
    val color: Color,
    val attempts: Int,
    val rating: Int,
    val categories: List<PuzzleCategory>,
    val moves: List<String>,
    val solution: List<String>,
)
