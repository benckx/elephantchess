package io.elephantchess.servicelayer.batch

import io.elephantchess.db.services.EmailVerificationDaoService
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.servicelayer.services.MailService
import io.elephantchess.servicelayer.services.MailService.Companion.emailValidityUpdateTime
import io.github.oshai.kotlinlogging.KotlinLogging

class VerifyEmailsBatch(
    private val mailService: MailService,
    private val emailVerificationDaoService: EmailVerificationDaoService,
) : SinglePodBatch {

    override val logger = KotlinLogging.logger {}
    override val podNumber: Int = 1

    override suspend fun run() {
        emailVerificationDaoService
            .listEmailsToVerify(emailValidityUpdateTime)
            .forEach { emailAddress ->
                mailService.verifyEmailAddress(emailAddress)
            }
    }

}
