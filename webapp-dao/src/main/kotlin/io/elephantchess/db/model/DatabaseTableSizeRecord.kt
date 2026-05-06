package io.elephantchess.db.model

data class DatabaseTableSizeRecord(
    val tableSchema: String,
    val tableName: String,
    val rowEstimate: Long,
    val totalBytes: Long,
    val indexBytes: Long,
    val toastBytes: Long,
    val tableBytes: Long,
    val total: String,
    val index: String,
    val toast: String,
    val table: String
)
