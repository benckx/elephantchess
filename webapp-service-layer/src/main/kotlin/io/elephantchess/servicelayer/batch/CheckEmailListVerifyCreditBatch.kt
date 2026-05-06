package io.elephantchess.servicelayer.batch

import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.servicelayer.clients.EmailListVerifyClient
import io.elephantchess.servicelayer.services.MailService
import io.github.oshai.kotlinlogging.KotlinLogging

class CheckEmailListVerifyCreditBatch(
    private val client: EmailListVerifyClient,
    private val mailService: MailService
) : SinglePodBatch {

    override val logger = KotlinLogging.logger {}
    override val podNumber: Int = 1

    override suspend fun run() {
        client.getCredits()?.onDemand?.available
            ?.let { availableCredit ->
                logger.info { "available credit: $availableCredit" }
                if (availableCredit <= 1_000) {
                    mailService.sendLowCreditNotification(availableCredit)
                }
            }
    }

}
