package io.elephantchess.db.model.analytics

data class NewsletterLinkClickRecord(
    val templateName: String,
    val link: String,
    val clickCount: Int,
)
