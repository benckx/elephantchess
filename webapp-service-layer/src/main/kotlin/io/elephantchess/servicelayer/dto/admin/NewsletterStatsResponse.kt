package io.elephantchess.servicelayer.dto.admin

data class NewsletterStatsResponse(
    val entries: List<Entry>,
    val potentialRecipients: Int
) {

    data class Entry(
        val templateName: String,
        val subject: String,
        val createdTime: Long?,
        val firstSentTime: Long?,
        val lastSentTime: Long?,
        val daysToSend: Long?,
        val totalEmails: Int,
        val sentCount: Int,
        val pendingCount: Int,
        val unsubscribedNewsletterCount: Int,
        val unsubscribedAllCount: Int,
        val linkClicks: Int
    )

}
