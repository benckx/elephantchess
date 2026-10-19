package io.elephantchess.servicelayer.dto.user

data class UserPageViewsSettingsResponse(
    val entries: List<Entry>,
    val total: Int,
) {
    data class Entry(
        val url: String,
        val time: Long,
    )
}
