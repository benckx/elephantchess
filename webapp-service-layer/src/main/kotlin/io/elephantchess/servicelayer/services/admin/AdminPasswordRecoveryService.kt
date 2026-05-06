package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.PasswordRecoveryAttemptsDaoService
import io.elephantchess.servicelayer.dto.admin.PasswordRecoveryAttemptsResponse
import io.elephantchess.servicelayer.services.UserCache

class AdminPasswordRecoveryService(
    private val passwordRecoveryAttemptsDaoService: PasswordRecoveryAttemptsDaoService,
    private val userCache: UserCache,
) {

    suspend fun listLatestPasswordRecoveryAttempts(): PasswordRecoveryAttemptsResponse {
        return passwordRecoveryAttemptsDaoService
            .listLatestAttempts(100)
            .map { record ->
                val matchingUserId = record.matchingUserId

                PasswordRecoveryAttemptsResponse.Entry(
                    creationTime = record.entryCreation.toEpochMilliseconds(),
                    email = record.emailProvided,
                    userId = matchingUserId,
                    username = matchingUserId?.let { userCache.fetchUsernameOrDefault(it) },
                    recoveryTime = record.dateRecovered?.toEpochMilliseconds(),
                    userCreation = matchingUserId?.let { userCache.fetchCreationTime(it) }?.toEpochMilliseconds(),
                )
            }
            .let { entries ->
                PasswordRecoveryAttemptsResponse(entries)
            }
    }

}
