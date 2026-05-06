package io.elephantchess.servicelayer.dto.admin

data class HourlyPageViewsResponse(
    val entries: List<Entry>
) {
    data class Entry(
        val hour: String,
        val pageViews: Int
    )
}
