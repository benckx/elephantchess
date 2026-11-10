package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.EmailVerificationDaoService
import io.elephantchess.model.SuppressingEmailStatus
import io.elephantchess.servicelayer.dto.admin.EmailBouncesResponse
import io.elephantchess.utils.di.KoinSingleton

@KoinSingleton
class AdminEmailBounceService(private val emailVerificationDaoService: EmailVerificationDaoService) {

    suspend fun listBounces(): EmailBouncesResponse {
        val records = emailVerificationDaoService.listBounceRecords(Int.MAX_VALUE, 0)
        return EmailBouncesResponse(
            entries = records.map {
                EmailBouncesResponse.Entry(
                    email = it.email,
                    bouncedTime = it.bouncedTime.toEpochMilliseconds(),
                    action = it.action,
                    status = it.status,
                    diagnostic = it.diagnostic,
                    suppressed = SuppressingEmailStatus.suppresses(it.status),
                )
            },
        )
    }
}
