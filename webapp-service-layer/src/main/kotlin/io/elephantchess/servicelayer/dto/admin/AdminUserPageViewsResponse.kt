package io.elephantchess.servicelayer.dto.admin

data class AdminUserPageViewsResponse(val entries: List<Entry>) {
    data class Entry(val url: String, val time: Long)
}
