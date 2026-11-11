package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.AnalysisStatus
import io.elephantchess.model.GameId

data class MoveAnnotationFailuresResponse(val entries: List<Entry>) {
    data class Entry(
        val gameId: GameId,
        val analysisStatus: AnalysisStatus?,
        val exceptionCount: Int,
        val lastExceptionTime: Long,
    )
}
