package io.elephantchess.servicelayer.dto.database

data class DatabasePlayerProfileVersionHistoryEntry(
    val versionIndex: Int,
    val editorUserId: String,
    val editorUsername: String,
    val versionTime: Long,
    val comment: String?,
    val canonicalName: String,
    val chineseName: String?,
    val gender: String?,
    val enabled: Boolean
)
