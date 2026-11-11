package io.elephantchess.servicelayer.dto.admin

data class EmailBouncesResponse(val entries: List<Entry>) {

    data class Entry(
        val email: String,
        val creationTime: Long,
        val updateTime: Long?,
        val action: String?,
        val status: String?,
        val diagnostic: String?,
        val suppressed: Boolean,
    )
}
