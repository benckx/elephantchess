package io.elephantchess.servicelayer.batch

import io.elephantchess.db.dao.codegen.tables.pojos.NewsletterEmail
import io.elephantchess.db.services.NewsletterDaoService
import io.elephantchess.db.utils.isBefore
import io.elephantchess.db.utils.plusSeconds
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.servicelayer.services.MailService
import io.github.oshai.kotlinlogging.KLogger
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Titan Pro Plan limits:
 * - 200 emails/hour
 * - 500 emails/day
 *
 * Bounce rate limit:
 * - Hourly 10
 * - Daily 20
 * - 3 days 50
 */
class SendOutNewslettersBatch(
    override val logger: KLogger,
    private val newsletterDaoService: NewsletterDaoService,
    private val mailService: MailService,
) : SinglePodBatch {

    override val podNumber: Int = 0

    // max 192 emails/day
    private val minDurationBetweenBatch = 15.minutes
    private val batchSize = 2

    override suspend fun run() {
        newsletterDaoService
            .listNewslettersToFanOut()
            .forEach { newsletterId ->
                logger.info { "fanning out newsletter: $newsletterId" }
                fanOutNewsletter(newsletterId)
            }

        val now = Clock.System.now()
        val lastSent = newsletterDaoService.getLastSentNewsLetterEmail()
        val minNextBatchTime = lastSent?.plusSeconds(minDurationBetweenBatch.inWholeSeconds)
        val shouldSendNextBatch = minNextBatchTime == null || minNextBatchTime.isBefore(now)

        if (shouldSendNextBatch) {
            newsletterDaoService
                .getBatchToSend(batchSize)
                .forEach { entry ->
                    try {
                        newsletterDaoService
                            .findNewsletterById(entry.newsletterId)
                            ?.let { newsletter ->
                                mailService.sendNewsLetter(
                                    recipient = entry.emailAddress,
                                    templateName = newsletter.templateName,
                                    subject = newsletter.subject,
                                    unsubscribeFromNewsletterCode = entry.unsubscribeFromNewsletterCode,
                                    unsubscribeFromAllCode = entry.unsubscribeFromAllCode
                                )

                                newsletterDaoService.markAsSent(
                                    newsletterId = entry.newsletterId,
                                    emailAddress = entry.emailAddress
                                )
                            }
                    } catch (e: Exception) {
                        logger.warn(e) { "error while sending newsletter to ${entry.emailAddress}" }
                    }
                }
        }
    }

    /**
     * create all required NEWSLETTER_EMAIL entries for the given NEWSLETTER
     */
    private suspend fun fanOutNewsletter(newsletterId: String) {
        val newsletterRecipients =
            mailService
                .listNewsLetterRecipientEmails()
                .shuffled() // emails should be sent out in a different order each time

        newsletterRecipients
            .map { emailAddress ->
                NewsletterEmail().also { entry ->
                    entry.newsletterId = newsletterId
                    entry.emailAddress = emailAddress
                    entry.unsubscribeFromAllCode = UUID.randomUUID().toString()
                    entry.unsubscribeFromNewsletterCode = UUID.randomUUID().toString()
                    entry.createdTime = Clock.System.now()
                }
            }
            .let { entries ->
                newsletterDaoService.insertAll(entries)
            }
    }

}
