package io.elephantchess.db.model.analytics

data class HourlyPageViewRecord(
    val hour: String,
    val uniquePageViews: Int
)
