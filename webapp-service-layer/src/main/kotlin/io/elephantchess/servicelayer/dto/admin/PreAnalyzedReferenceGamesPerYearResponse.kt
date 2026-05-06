package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.AnalysisStatus

data class PreAnalyzedReferenceGamesPerYearResponse(val entries: List<Entry>) {

    data class Entry(
        val year: Int,
        val status: AnalysisStatus,
        val count: Int,
    )

}
