package io.elephantchess.servicelayer.dto.analysis

data class RenameAnalysisRequest(
    val analysisId: String,
    val name: String
)
