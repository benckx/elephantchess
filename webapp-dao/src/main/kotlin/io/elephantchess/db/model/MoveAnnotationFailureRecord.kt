package io.elephantchess.db.model

import io.elephantchess.model.GameId
import kotlin.time.Instant

data class MoveAnnotationFailureRecord(
    val gameId: GameId,
    val exceptionCount: Int,
    val lastExceptionTime: Instant,
)
