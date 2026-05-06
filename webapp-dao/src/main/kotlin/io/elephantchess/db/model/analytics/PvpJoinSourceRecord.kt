package io.elephantchess.db.model.analytics

import java.time.YearMonth

data class PvpJoinSourceRecord(
    val month: YearMonth,
    val joinSource: String,
    val count: Int
)
