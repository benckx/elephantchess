package io.elephantchess.db.model

import java.time.YearMonth

data class ThrownExceptionMonthlyCountRecord(
    val month: YearMonth,
    val clientErrors: Int, // HTTP 4xx
    val serverErrors: Int, // HTTP 5xx
)
