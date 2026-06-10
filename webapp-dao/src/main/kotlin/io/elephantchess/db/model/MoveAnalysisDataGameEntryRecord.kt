package io.elephantchess.db.model

import io.elephantchess.model.GameId
import kotlin.time.Instant

data class MoveAnalysisDataGameEntryRecord(
    val gameId: GameId,
    val first: Instant,
    val last: Instant,
    val totalAnalyzedMoves: Int,
    val analyzedFromBatch: Boolean = false,
)
