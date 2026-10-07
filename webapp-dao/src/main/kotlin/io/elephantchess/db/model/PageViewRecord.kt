package io.elephantchess.db.model

data class PageViewRecord(
    val url: String,
    val eventTime: kotlin.time.Instant,
)
