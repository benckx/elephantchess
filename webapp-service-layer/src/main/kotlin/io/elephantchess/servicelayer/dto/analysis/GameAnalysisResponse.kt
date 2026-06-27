package io.elephantchess.servicelayer.dto.analysis

import io.elephantchess.servicelayer.analysis.MoveAnnotationCategory
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto

data class GameAnalysisResponse(
    val entries: List<InfoLineResultDto>,
    val moveAnnotations: List<MoveAnnotationDto> = emptyList(),
) {
    data class MoveAnnotationDto(
        val moveIndex: Int,
        val annotation: MoveAnnotationCategory,
        val cpl: Int,
        val engineCp: Int,
        val actualMoveCp: Int,
    )
}
