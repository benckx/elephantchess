package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.model.UserSessionRecord
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.servicelayer.dto.admin.UserSessionsResponse
import io.elephantchess.servicelayer.services.UserCache

class AdminUserSessionService(
    private val userSessionDaoService: UserSessionDaoService,
    private val userCache: UserCache,
) {

    suspend fun listAuthenticatedUserSessions(): UserSessionsResponse {
        val entries =
            userSessionDaoService
                .listAuthenticatedSessions(200)
                .map { record -> mapUserSessionToDto(record) }

        return UserSessionsResponse(entries)
    }

    private suspend fun mapUserSessionToDto(userSession: UserSessionRecord): UserSessionsResponse.Entry {
        return UserSessionsResponse.Entry(
            userId = userSession.userId,
            username = userSession.userId?.let { userCache.fetchUsernameOrDefault(it) },
            os = userSession.operatingSystemName,
            agentName = userSession.agentName,
            agentClass = userSession.agentClass,
            countryCode = userSession.countryCode,
            countryName = userSession.countryName,
            region = userSession.region,
            city = userSession.city,
            remoteAddress = userSession.remoteAddress,
            created = userSession.created!!.toEpochMilliseconds(),
            updated = userSession.lastUpdated!!.toEpochMilliseconds()
        )
    }

}
