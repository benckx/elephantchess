package io.elephantchess.servicelayer.dto.database

data class ListUserEdits(val entries: List<Entry>) {

    data class Entry(
        val playerId: String,
        val playerCanonicalName: String,
        val playerChineseName: String?,
        val version: Int,
        val latestEditorId: String,
        val latestEditorUsername: String,
        val latestComment: String?,
        val latestEditTimestamp: Long,
        val enabled: Boolean
    )

}
