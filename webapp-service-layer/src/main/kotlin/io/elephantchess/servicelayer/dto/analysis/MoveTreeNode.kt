package io.elephantchess.servicelayer.dto.analysis

data class MoveTreeNode(
    val id: String,
    val move: String,
    val level: Int,
    val previous: String?,
    val next: String?,
    val childNodes: List<String>,
    val annotation: String?,
)
