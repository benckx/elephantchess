package io.elephantchess.servicelayer.dto.admin

data class EmailBouncesResponse(val entries: List<Entry>, val hasMore: Boolean) {

    data class Entry(
        val email: String,
        val bouncedTime: Long,
        val action: String?,
        val status: String?,
        val diagnostic: String?,
        val suppressed: Boolean,
    )
}
