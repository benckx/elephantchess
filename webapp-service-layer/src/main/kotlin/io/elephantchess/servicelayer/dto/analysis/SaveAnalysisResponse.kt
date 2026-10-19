package io.elephantchess.servicelayer.dto.analysis

data class SaveAnalysisResponse(
    val analysisId: String,
    val version: Int,
    val lastUpdated: Long,
)
