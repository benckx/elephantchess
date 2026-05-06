package io.elephantchess.servicelayer.dto.analysis

import io.elephantchess.model.AnalysisStatus

data class StartGameAnalysisResponse(
    val status: AnalysisStatus,
    val hasStarted: Boolean,
)
