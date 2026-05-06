package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.ThrownExceptionDaoService
import io.elephantchess.servicelayer.dto.admin.ThrownExceptionsResponse

class AdminExceptionService(
    private val thrownExceptionDaoService: ThrownExceptionDaoService,
) {

    suspend fun listLatestExceptions(limit: Int, httpCodeFilter: String?): ThrownExceptionsResponse {
        return thrownExceptionDaoService
            .listLatestExceptions(limit, httpCodeFilter)
            .map { record ->
                ThrownExceptionsResponse.Entry(
                    exceptionTime = record.exceptionTime.toEpochMilliseconds(),
                    httpCode = record.httpCode,
                    exceptionClass = record.exceptionClass,
                    exceptionMessage = record.exceptionMessage,
                )
            }
            .let { entries ->
                ThrownExceptionsResponse(entries)
            }
    }

}
