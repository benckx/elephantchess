package io.elephantchess.servicelayer.dto.admin

data class DatabaseTableSizeResponse(val entries: List<Entry>) {

    data class Entry(
        val tableSchema: String,
        val tableName: String,
        val rowEstimate: Long,
        val totalBytes: Long,
        val indexBytes: Long,
        val toastBytes: Long,
        val tableBytes: Long,
        val totalSize: String,
        val indexSize: String,
        val toastSize: String,
        val tableSize: String
    )

}
