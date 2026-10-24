package io.elephantchess.servicelayer.services.admin

import io.elephantchess.config.AppConfig
import io.elephantchess.db.model.analytics.NewsletterLinkClickRecord
import io.elephantchess.db.services.ArchivedPageViewDaoService
import io.elephantchess.db.services.NewsletterDaoService
import io.elephantchess.db.services.PageViewEventDaoService
import io.elephantchess.servicelayer.dto.admin.NewsletterStatsResponse
import io.elephantchess.servicelayer.services.MailService
import io.elephantchess.utils.di.KoinSingleton

@KoinSingleton
class AdminNewsletterService(
    private val newsletterDaoService: NewsletterDaoService,
    private val pageViewEventDaoService: PageViewEventDaoService,
    private val archivedPageViewDaoService: ArchivedPageViewDaoService,
    private val mailService: MailService,
    appConfig: AppConfig,
) {

    private val excludedUserIds = appConfig.excludedFromAnalytics

    suspend fun fetchNewsletterStats(): NewsletterStatsResponse {
        val potentialRecipients = mailService.listNewsLetterRecipientEmails().size
        val clicksPerTemplate = mergeClicksPerTemplate(
            current = pageViewEventDaoService.countNewsletterClicksPerTemplate(excludedUserIds),
            archived = archivedPageViewDaoService.countArchivedNewsletterClicksPerTemplate(),
        )
        val linkClicksPerTemplate = mergeLinkClicks(
            current = pageViewEventDaoService.listNewsletterClicksPerTemplateAndLink(excludedUserIds),
            archived = archivedPageViewDaoService.listArchivedNewsletterClicksPerTemplateAndLink(),
        )
            .groupBy { it.templateName }

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
                    linkClicks = clicksPerTemplate[record.templateName] ?: 0,
                    linkClickDetails = (linkClicksPerTemplate[record.templateName] ?: emptyList())
                        .map { NewsletterStatsResponse.LinkClickDetail(it.link, it.clickCount) },
                )
            }
            .let { entries ->
                NewsletterStatsResponse(entries, potentialRecipients)
            }
    }

    private companion object {

        fun mergeClicksPerTemplate(current: Map<String, Int>, archived: Map<String, Int>): Map<String, Int> {
            if (archived.isEmpty()) {
                return current
            }

            val merged = current.toMutableMap()
            archived.forEach { (templateName, clickCount) ->
                merged.merge(templateName, clickCount, Int::plus)
            }
            return merged
        }

        fun mergeLinkClicks(
            current: List<NewsletterLinkClickRecord>,
            archived: List<NewsletterLinkClickRecord>,
        ): List<NewsletterLinkClickRecord> {
            if (archived.isEmpty()) {
                return current
            }

            return (current + archived)
                .groupBy { it.templateName to it.link }
                .map { (key, records) ->
                    NewsletterLinkClickRecord(
                        templateName = key.first,
                        link = key.second,
                        clickCount = records.sumOf { it.clickCount },
                    )
                }
                .sortedByDescending { it.clickCount }
        }
    }
}
