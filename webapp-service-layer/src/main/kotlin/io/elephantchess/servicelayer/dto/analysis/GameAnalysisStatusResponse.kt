package io.elephantchess.servicelayer.dto.analysis

import io.elephantchess.model.AnalysisStatus

data class GameAnalysisStatusResponse(
    val status: AnalysisStatus,
    val progress: Float,
)
