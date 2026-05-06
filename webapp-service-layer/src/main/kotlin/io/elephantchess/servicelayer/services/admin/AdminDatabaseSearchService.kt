package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.servicelayer.dto.admin.ReferenceGameSearchQueryResponse
import io.elephantchess.servicelayer.services.UserCache

class AdminDatabaseSearchService(
    private val referenceGameDaoService: ReferenceGameDaoService,
    private val userCache: UserCache,
) {

    suspend fun listLatestSearchQueries(): ReferenceGameSearchQueryResponse {
        return referenceGameDaoService
            .listLatestDatabaseSearches(100)
            .map { record ->
                ReferenceGameSearchQueryResponse.Entry(
                    queryId = record.queryId,
                    queryTime = record.queryTime.toEpochMilliseconds(),
                    userId = record.userId,
                    userType = userCache.fetchUserType(record.userId)!!,
                    username = userCache.fetchUsernameOrDefault(record.userId),
                    searchStart = record.searchStart?.toString(),
                    searchEnd = record.searchEnd?.toString(),
                    playerName = record.playerName,
                    playerColor = record.playerColor,
                    eventName = record.eventName,
                    fen = record.fen,
                    offset = record.offset,
                    numberOfResults = record.numberOfResults
                )
            }.let { entries ->
                ReferenceGameSearchQueryResponse(entries)
            }
    }

}
