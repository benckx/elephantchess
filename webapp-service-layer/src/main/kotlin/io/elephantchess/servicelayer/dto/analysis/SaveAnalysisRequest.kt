package io.elephantchess.servicelayer.dto.analysis

import io.elephantchess.model.GameId

data class SaveAnalysisRequest(
    val analysisId: String?,
    val name: String,
    val nodes: List<MoveTreeNode>,
    val gameId: GameId?,
    val engineCache: List<PvCache>,
    val startFen: String?,
    val selectedNodeId: String?,
    val openBranchIds: List<String> = listOf(),
)
