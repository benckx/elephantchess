package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.AnalysisStatus
import io.elephantchess.model.GameType

data class PreAnalysisStatusByGameTypeResponse(val entries: List<Entry>) {

    data class Entry(
        val gameType: GameType,
        val status: AnalysisStatus,
        val count: Int,
    )

}
