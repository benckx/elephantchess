package io.elephantchess.servicelayer.dto.admin

data class AdminUpcomingEventsResponse(val events: List<AdminUpcomingEventEntry>)

data class AdminUpcomingEventEntry(
    val id: Int,
    val startDate: String,
    val endDate: String,
    val description: String,
    val link: String,
    val isEnabled: Boolean,
    val createdAt: Long?,
    val createdByUsername: String?
)

data class CreateUpcomingEventRequest(
    val startDate: String,
    val endDate: String,
    val description: String,
    val link: String
)

data class CreateUpcomingEventResponse(val id: Int)

data class UpdateUpcomingEventRequest(
    val id: Int,
    val startDate: String,
    val endDate: String,
    val description: String,
    val link: String
)

data class ToggleUpcomingEventRequest(
    val id: Int,
    val enabled: Boolean
)

