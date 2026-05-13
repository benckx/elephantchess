package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.ReferencePlayerDaoService
import io.elephantchess.servicelayer.dto.admin.PlayerDuplicatesResponse
import io.elephantchess.servicelayer.dto.database.DatabasePlayerProfileVersionHistoryEntry
import io.elephantchess.servicelayer.dto.database.DatabasePlayerVersionHistory
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.exceptions.NotFoundException
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

    suspend fun listPlayerDuplicates(): PlayerDuplicatesResponse {
        return referencePlayerDaoService
            .listAllPlayerDuplicates()
            .mapNotNull { duplicate ->
                val player = referencePlayerDaoService.findPlayer(duplicate.playerId) ?: return@mapNotNull null
                val canonicalPlayer =
                    referencePlayerDaoService.findPlayer(duplicate.isDuplicateOf) ?: return@mapNotNull null
                PlayerDuplicatesResponse.Entry(
                    playerId = duplicate.playerId,
                    playerCanonicalName = player.canonicalName,
                    isDuplicateOf = duplicate.isDuplicateOf,
                    canonicalPlayerCanonicalName = canonicalPlayer.canonicalName
                )
            }
            .let { entries -> PlayerDuplicatesResponse(entries) }
    }

    suspend fun registerPlayerDuplicate(playerId: String, isNewDuplicateOf: String) {
        if (playerId == isNewDuplicateOf) {
            throw BadRequestException("a player cannot be a duplicate of itself")
        }
        referencePlayerDaoService.findPlayer(playerId)
            ?: throw NotFoundException("player $playerId not found")
        referencePlayerDaoService.findPlayer(isNewDuplicateOf)
            ?: throw NotFoundException("player $isNewDuplicateOf not found")

        // prevent cycles: the canonical player must not itself be registered as a duplicate
        val canonicalIsAlreadyDuplicate = referencePlayerDaoService.findCanonicalPlayerFor(isNewDuplicateOf)
        if (canonicalIsAlreadyDuplicate != null) {
            throw BadRequestException(
                "player $isNewDuplicateOf is already registered as a duplicate of $canonicalIsAlreadyDuplicate; " +
                        "resolve that relationship first"
            )
        }

        // prevent reverse cycle: the new duplicate must not already be the canonical for others
        val playersAlreadyDuplicateOf = referencePlayerDaoService.findConfirmedDuplicatesOf(playerId)
        if (playersAlreadyDuplicateOf.isNotEmpty()) {
            throw BadRequestException(
                "player $playerId is already listed as a canonical player for other duplicates; " +
                        "remove those relationships first"
            )
        }

        referencePlayerDaoService.savePlayerDuplicate(playerId, isNewDuplicateOf)
    }

    suspend fun deletePlayerDuplicate(playerId: String) {
        referencePlayerDaoService.deletePlayerDuplicate(playerId)
    }

}
