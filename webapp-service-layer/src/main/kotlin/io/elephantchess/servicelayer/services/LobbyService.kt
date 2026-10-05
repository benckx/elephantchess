package io.elephantchess.servicelayer.services

import io.elephantchess.utils.di.KoinSingleton

import io.elephantchess.db.services.UpcomingEventDaoService
import io.elephantchess.servicelayer.dto.lobby.GetUpcomingEventsResponse
import io.elephantchess.servicelayer.dto.lobby.GetUpcomingEventsResponse.UpcomingEvent

@KoinSingleton
class LobbyService(
    private val upcomingEventDaoService: UpcomingEventDaoService
) {

    suspend fun listUpcomingEvents(): GetUpcomingEventsResponse {
        return upcomingEventDaoService
            .listUpcomingEventsForLobby()
            .map { event ->
                UpcomingEvent(
                    start = event.eventStart.toString(),
                    end = event.eventEnd.toString(),
                    description = event.description,
                    link = event.link,
                )
            }
            .let { events ->
                GetUpcomingEventsResponse(events)
            }
    }

}
