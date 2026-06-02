package io.elephantchess.servicelayer.services

import io.elephantchess.db.services.UpcomingEventDaoService
import io.elephantchess.model.GameId
import io.elephantchess.servicelayer.dto.lobby.GetUpcomingEventsResponse
import io.elephantchess.servicelayer.dto.lobby.GetUpcomingEventsResponse.UpcomingEvent
import io.elephantchess.servicelayer.dto.lobby.LatestGamesUpdateResponse
import io.elephantchess.servicelayer.services.ws.LiveGamesWebSocketSession
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRate
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ChannelResult
import kotlin.time.Duration.Companion.seconds

class LobbyService(
    private val upcomingEventDaoService: UpcomingEventDaoService,
    private val gameDataService: GameDataService,
    refresherScope: CoroutineScope,
    private val logger: KLogger,
) {

    private val sessionsRefresh = 1.seconds
    private val liveGamesSessions = mutableListOf<LiveGamesWebSocketSession>()

    private val refreshJob = launchAtFixedRate(
        scope = refresherScope,
        initialDelay = sessionsRefresh,
        period = sessionsRefresh,
        action = { refreshLiveGamesSessions() }
    )

    fun cancel() {
        refreshJob.cancel()
    }

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

    fun startLiveGamesSession(
        sendCb: (LatestGamesUpdateResponse) -> ChannelResult<Unit>,
    ): String {
        val session = LiveGamesWebSocketSession(sendCb)
        liveGamesSessions.add(session)
        logger.debug { "created $session" }
        return session.sessionId
    }

    fun handleLiveGamesSubscription(sessionId: String, gameIds: List<GameId>) {
        liveGamesSessions
            .find { it.sessionId == sessionId }
            ?.updateSubscription(gameIds)
    }

    fun closeLiveGamesSession(sessionId: String) {
        liveGamesSessions
            .find { it.sessionId == sessionId }
            ?.markAsClosed()
    }

    private suspend fun refreshLiveGamesSessions() {
        if (liveGamesSessions.isNotEmpty()) {
            // TODO: not very optimized: lobby sessions watch mostly the same games -> some
            //  information is fetched once per session instead of being batched across sessions
            liveGamesSessions.forEach { session ->
                val request = session.currentRequest()
                if (request.gameIds.isNotEmpty()) {
                    session.update(gameDataService.fetchLatestGamesUpdate(request))
                }
            }

            // remove the sessions that are not active anymore
            liveGamesSessions.removeIf { session ->
                if (session.isClosed) logger.debug { "removing $session" }
                session.isClosed
            }
        }
    }

}
