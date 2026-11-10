package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.EmailVerificationDaoService
import io.elephantchess.model.SuppressingEmailStatus
import io.elephantchess.servicelayer.dto.admin.EmailBouncesResponse
import io.elephantchess.utils.di.KoinSingleton

@KoinSingleton
class AdminEmailBounceService(private val emailVerificationDaoService: EmailVerificationDaoService) {

    suspend fun listBounces(offset: Int): EmailBouncesResponse {
        require(offset >= 0) { "offset must not be negative" }
        val records = emailVerificationDaoService.listBounceRecords(PAGE_SIZE + 1, offset)
        return EmailBouncesResponse(
            entries = records.take(PAGE_SIZE).map {
                EmailBouncesResponse.Entry(
                    email = it.email,
                    bouncedTime = it.bouncedTime.toEpochMilliseconds(),
                    action = it.action,
                    status = it.status,
                    diagnostic = it.diagnostic,
                    suppressed = SuppressingEmailStatus.suppresses(it.status),
                )
            },
            hasMore = records.size > PAGE_SIZE,
        )
    }

    companion object {

        const val PAGE_SIZE = 100
    }
}
