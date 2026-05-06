package io.elephantchess.db.model.analytics

import java.time.YearMonth

data class MonthlyPageViewRecord(
    val yearMonth: YearMonth,
    val label: String,
    val uniquePageViews: Int
)
