package io.elephantchess.servicelayer.dto.admin

data class DailyPageViewsResponse(
    val entries: List<Entry>
) {
    data class Entry(
        val day: String,
        val pageViews: Int
    )
}
