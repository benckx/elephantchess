package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.dao.codegen.tables.pojos.UpcomingEvent
import io.elephantchess.db.services.UpcomingEventDaoService
import io.elephantchess.servicelayer.dto.admin.*
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.services.UserCache
import org.apache.commons.validator.routines.UrlValidator
import java.time.LocalDate
import kotlin.time.Clock

class AdminUpcomingEventsService(
    private val upcomingEventDaoService: UpcomingEventDaoService,
    private val userCache: UserCache
) {

    private val urlValidator = UrlValidator(arrayOf("http", "https"))

    suspend fun listAllEvents(): AdminUpcomingEventsResponse {
        val events = upcomingEventDaoService.listAllUpcomingEvents()
            .map { event ->
                val createdByUsername = event.createdBy?.let { userCache.fetchUsername(it) }
                AdminUpcomingEventEntry(
                    id = event.id,
                    startDate = event.eventStart.toString(),
                    endDate = event.eventEnd.toString(),
                    description = event.description,
                    link = event.link,
                    isEnabled = event.isEnabled,
                    createdAt = event.createdAt?.toEpochMilliseconds(),
                    createdByUsername = createdByUsername
                )
            }
        return AdminUpcomingEventsResponse(events)
    }

    suspend fun createEvent(userId: String, request: CreateUpcomingEventRequest): CreateUpcomingEventResponse {
        val startDate = LocalDate.parse(request.startDate)
        val endDate = LocalDate.parse(request.endDate)

        // validation
        if (startDate > endDate) {
            throw BadRequestException("Start date must be before or equal to end date")
        }
        if (request.description.isBlank()) {
            throw BadRequestException("Description cannot be empty")
        }
        if (!urlValidator.isValid(request.link)) {
            throw BadRequestException("Link must be a valid URL (http or https)")
        }

        val upcomingEvent = UpcomingEvent()
        upcomingEvent.eventStart = startDate
        upcomingEvent.eventEnd = endDate
        upcomingEvent.description = request.description
        upcomingEvent.link = request.link
        upcomingEvent.createdBy = userId
        upcomingEvent.createdAt = Clock.System.now()

        val newId = upcomingEventDaoService.save(upcomingEvent)

        return CreateUpcomingEventResponse(id = newId)
    }

    suspend fun updateEvent(request: UpdateUpcomingEventRequest) {
        val startDate = LocalDate.parse(request.startDate)
        val endDate = LocalDate.parse(request.endDate)

        // validation
        if (startDate > endDate) {
            throw BadRequestException("Start date must be before or equal to end date")
        }
        if (request.description.isBlank()) {
            throw BadRequestException("Description cannot be empty")
        }
        if (!urlValidator.isValid(request.link)) {
            throw BadRequestException("Link must be a valid URL (http or https)")
        }

        val existingEvent = upcomingEventDaoService.findById(request.id)
            ?: throw NotFoundException("Event not found")

        existingEvent.eventStart = startDate
        existingEvent.eventEnd = endDate
        existingEvent.description = request.description
        existingEvent.link = request.link

        upcomingEventDaoService.updateEvent(existingEvent)
    }

    suspend fun toggleEnabled(request: ToggleUpcomingEventRequest) {
        val existingEvent = upcomingEventDaoService.findById(request.id)
            ?: throw NotFoundException("Event not found")

        upcomingEventDaoService.toggleEnabled(existingEvent.id, request.enabled)
    }

}
