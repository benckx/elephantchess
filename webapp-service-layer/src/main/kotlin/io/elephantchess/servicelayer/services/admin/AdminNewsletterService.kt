package io.elephantchess.servicelayer.services.admin

import io.elephantchess.config.AppConfig
import io.elephantchess.db.services.NewsletterDaoService
import io.elephantchess.db.services.PageViewEventDaoService
import io.elephantchess.servicelayer.dto.admin.NewsletterStatsResponse
import io.elephantchess.servicelayer.services.MailService

class AdminNewsletterService(
    private val newsletterDaoService: NewsletterDaoService,
    private val pageViewEventDaoService: PageViewEventDaoService,
    private val mailService: MailService,
    appConfig: AppConfig
) {

    private val excludedUserIds = appConfig.excludedFromAnalytics

    suspend fun fetchNewsletterStats(): NewsletterStatsResponse {
        val potentialRecipients = mailService.listNewsLetterRecipientEmails().size
        val clicksPerTemplate = pageViewEventDaoService.countNewsletterClicksPerTemplate(excludedUserIds)

        return newsletterDaoService
            .fetchNewsletterStats()
            .map { record ->
                val firstSent = record.firstSentTime
                val lastSent = record.lastSentTime
                val daysToSend = if (firstSent != null && lastSent != null) {
                    (lastSent - firstSent).inWholeDays + 1
                } else {
                    null
                }

                NewsletterStatsResponse.Entry(
                    templateName = record.templateName,
                    subject = record.subject,
                    createdTime = record.createdTime?.toEpochMilliseconds(),
                    firstSentTime = record.firstSentTime?.toEpochMilliseconds(),
                    lastSentTime = record.lastSentTime?.toEpochMilliseconds(),
                    daysToSend = daysToSend,
                    totalEmails = record.totalEmails,
                    sentCount = record.sentCount,
                    pendingCount = record.totalEmails - record.sentCount,
                    unsubscribedNewsletterCount = record.unsubscribedNewsletterCount,
                    unsubscribedAllCount = record.unsubscribedAllCount,
                    linkClicks = clicksPerTemplate[record.templateName] ?: 0
                )
            }
            .let { entries ->
                NewsletterStatsResponse(entries, potentialRecipients)
            }
    }

}
