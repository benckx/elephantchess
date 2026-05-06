package io.elephantchess.servicelayer.dto.analysis

import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto

data class GameAnalysisResponse(val entries: List<InfoLineResultDto>)
