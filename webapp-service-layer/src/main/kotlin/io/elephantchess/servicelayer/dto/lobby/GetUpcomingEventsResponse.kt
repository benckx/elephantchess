package io.elephantchess.servicelayer.dto.lobby

data class GetUpcomingEventsResponse(val events: List<UpcomingEvent>) {

    data class UpcomingEvent(
        val start: String,
        val end: String,
        val description: String,
        val link: String,
    )

}
