package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.DatabaseAdminDaoService
import io.elephantchess.servicelayer.dto.admin.DatabaseTableSizeResponse

class AdminPostgresService(
    private val databaseAdminDaoService: DatabaseAdminDaoService
) {

    suspend fun fetchTableSizes(): DatabaseTableSizeResponse {
        return databaseAdminDaoService
            .fetchTableSizes()
            .map { record ->
                DatabaseTableSizeResponse.Entry(
                    tableSchema = record.tableSchema,
                    tableName = record.tableName,
                    rowEstimate = if (record.rowEstimate < 0) 0 else record.rowEstimate,
                    totalBytes = record.totalBytes,
                    indexBytes = record.indexBytes,
                    toastBytes = record.toastBytes,
                    tableBytes = record.tableBytes,
                    totalSize = record.total,
                    indexSize = record.index,
                    toastSize = record.toast,
                    tableSize = record.table
                )
            }
            .let { entries ->
                DatabaseTableSizeResponse(entries)
            }
    }

}
