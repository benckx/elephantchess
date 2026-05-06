package io.elephantchess.servicelayer.dto.analysis

import io.elephantchess.model.GameId

data class GetAnalysisResponse(
    val analysisId: String,
    val version: Int,
    val userId: String,
    val username: String,
    val name: String,
    val nodes: List<MoveTreeNode>,
    val lastUpdated: Long,
    val gameId: GameId?,
    val startFen: String?,
    val selectedNodeId: String?,
    val openBranchIds: List<String>,
)
