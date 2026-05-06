package io.elephantchess.servicelayer.dto.analysis

import io.elephantchess.model.GameType

data class ListUserAnalysisResponse(val entries: List<Entry>) {

    data class Entry(
        val analysisId: String,
        val currentVersion: Int,
        val name: String,
        val created: Long,
        val lastUpdated: Long,
        val versions: List<Int>,
        val gameType: GameType?,
        val selectedNodeFen: String?,
        val numberOfAnnotations: Int,
        val numberOfVariations: Int,
    )

}
