package io.elephantchess.db.model

import io.elephantchess.db.dao.codegen.tables.pojos.Puzzle
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleCategoryTag
import io.elephantchess.db.dao.codegen.tables.pojos.PuzzleHalfMove

data class PuzzleRecord(
    val puzzle: Puzzle,
    val moves: List<PuzzleHalfMove>,
    val categories: List<PuzzleCategoryTag>,
)
