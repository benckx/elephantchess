package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.ReferencePlayerDaoService
import io.elephantchess.servicelayer.dto.database.DatabasePlayerProfileVersionHistoryEntry
import io.elephantchess.servicelayer.dto.database.DatabasePlayerVersionHistory
import io.elephantchess.servicelayer.services.UserCache

class AdminDatabaseService(
    private val referencePlayerDaoService: ReferencePlayerDaoService,
    private val userCache: UserCache
) {

    suspend fun listLatestPlayerProfileVersions(limit: Int): DatabasePlayerVersionHistory {
        return referencePlayerDaoService
            .findLatestProfileVersions(limit)
            .map { version ->
                DatabasePlayerProfileVersionHistoryEntry(
                    versionIndex = version.version,
                    editorUserId = version.editorId,
                    editorUsername = userCache.fetchUsernameOrDefault(version.editorId),
                    versionTime = version.versionTime.toEpochMilliseconds(),
                    comment = version.comment,
                    canonicalName = version.canonicalName,
                    chineseName = version.chineseName,
                    gender = version.gender,
                    enabled = version.enabled
                )
            }.let { entries ->
                DatabasePlayerVersionHistory(entries)
            }
    }

}
