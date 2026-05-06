package io.elephantchess.servicelayer.dto.database

data class DatabasePlayerUpdateRequest(
    val playerId: String,
    val canonicalName: String?,
    val chineseName: String?,
    val gender: String?,
    val profileText: String?,
    val sources: List<DatabasePlayerProfileSource>,
    val editComment: String
)
