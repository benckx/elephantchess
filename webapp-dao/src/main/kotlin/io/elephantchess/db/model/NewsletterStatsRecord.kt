package io.elephantchess.db.model

import kotlin.time.Instant


data class NewsletterStatsRecord(
    val newsletterId: String,
    val templateName: String,
    val subject: String,
    val createdTime: Instant?,
    val firstSentTime: Instant?,
    val lastSentTime: Instant?,
    val totalEmails: Int,
    val sentCount: Int,
    val unsubscribedNewsletterCount: Int,
    val unsubscribedAllCount: Int
)
