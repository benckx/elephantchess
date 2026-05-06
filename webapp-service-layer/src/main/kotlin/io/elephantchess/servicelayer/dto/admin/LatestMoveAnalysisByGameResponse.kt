package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.AnalysisStatus
import io.elephantchess.model.GameId

data class LatestMoveAnalysisByGameResponse(val entries: List<Entry>) {

    data class Entry(
        val gameId: GameId,
        val first: Long,
        val last: Long,
        val totalAnalyzedMoves: Int,
        val analysisStatus: AnalysisStatus
    )

}
