package io.elephantchess.servicelayer.dto.admin

data class ThrownExceptionStatsByMonthResponse(
    val entries: List<Entry>
) {
    data class Entry(
        val month: String, // ISO format YYYY-MM
        val clientErrors: Int, // HTTP 4xx
        val serverErrors: Int, // HTTP 5xx
    )
}
