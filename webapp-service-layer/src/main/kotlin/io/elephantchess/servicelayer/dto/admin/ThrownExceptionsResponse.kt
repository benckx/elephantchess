package io.elephantchess.servicelayer.dto.admin

data class ThrownExceptionsResponse(val entries: List<Entry>) {

    data class Entry(
        val exceptionTime: Long,
        val httpCode: Int,
        val exceptionClass: String,
        val exceptionMessage: String,
    )

}
