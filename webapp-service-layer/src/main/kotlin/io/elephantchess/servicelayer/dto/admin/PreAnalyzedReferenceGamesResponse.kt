package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.AnalysisStatus

data class PreAnalyzedReferenceGamesResponse(val entries: List<Entry>) {

    data class Entry(
        val id: String,
        val status: AnalysisStatus,
        val numberOfMoves: Int,
        val numberOfMovesAnalyzed: Int,
        val start: Long,
        val end: Long?,
    )

}
