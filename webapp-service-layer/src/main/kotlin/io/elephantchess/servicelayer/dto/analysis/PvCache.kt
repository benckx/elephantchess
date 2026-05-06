package io.elephantchess.servicelayer.dto.analysis

data class PvCache(
    val fenKey: String,
    val line: String?,
    val depth: Int?,
    val cp: Int?,
    val mate: Int?,
    val pv: List<String>,
    val isCheckmate: Boolean = false,
)
