package io.elephantchess.servicelayer.dto.database

data class EventsListResponse(
    val entries: List<Entry>
) {

    data class Entry(
        val id: String,
        val name: String,
        val date: String?,
        val maxRound: Int?,
        val gameCount: Int
    )

}
