package io.elephantchess.db.services

import io.elephantchess.db.model.DatabaseTableSizeRecord
import io.elephantchess.db.utils.awaitRecords
import org.jooq.DSLContext

class DatabaseAdminDaoService(private val dslContext: DSLContext) {

    suspend fun fetchTableSizes(): List<DatabaseTableSizeRecord> {
        val records = dslContext
            .resultQuery(
                """
                SELECT *
                     , pg_size_pretty(total_bytes) AS total
                     , pg_size_pretty(index_bytes) AS index
                     , pg_size_pretty(toast_bytes) AS toast
                     , pg_size_pretty(table_bytes) AS table
                FROM (SELECT *, total_bytes - index_bytes - coalesce(toast_bytes, 0) AS table_bytes
                      FROM (SELECT c.oid
                                 , nspname                               AS table_schema
                                 , relname                               AS table_name
                                 , c.reltuples                           AS row_estimate
                                 , pg_total_relation_size(c.oid)         AS total_bytes
                                 , pg_indexes_size(c.oid)                AS index_bytes
                                 , pg_total_relation_size(reltoastrelid) AS toast_bytes
                            FROM pg_class c
                                     LEFT JOIN pg_namespace n ON n.oid = c.relnamespace
                            WHERE relkind = 'r' AND nspname = 'public') a) a
                ORDER BY total_bytes DESC
                """
            )
            .awaitRecords()

        return records.map { record ->
            val toastBytes = record.get("toast_bytes", Long::class.java)
            DatabaseTableSizeRecord(
                tableSchema = record.get("table_schema", String::class.java) ?: "",
                tableName = record.get("table_name", String::class.java) ?: "",
                rowEstimate = (record.get("row_estimate", Double::class.java) ?: 0.0).toLong(),
                totalBytes = record.get("total_bytes", Long::class.java) ?: 0L,
                indexBytes = record.get("index_bytes", Long::class.java) ?: 0L,
                toastBytes = toastBytes ?: 0L,
                tableBytes = record.get("table_bytes", Long::class.java) ?: 0L,
                total = record.get("total", String::class.java) ?: "0 bytes",
                index = record.get("index", String::class.java) ?: "0 bytes",
                toast = record.get("toast", String::class.java) ?: "0 bytes",
                table = record.get("table", String::class.java) ?: "0 bytes"
            )
        }
    }

}
