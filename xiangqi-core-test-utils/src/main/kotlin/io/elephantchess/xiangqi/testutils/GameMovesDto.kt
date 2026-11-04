package io.elephantchess.xiangqi.testutils

data class GameMovesDto(
    val gameId: String,
    val uciMoves: List<String>,
    val outcome: String? = null,
)
