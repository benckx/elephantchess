package io.elephantchess.servicelayer.services

import io.elephantchess.db.services.UpcomingEventDaoService
import io.elephantchess.model.GameId
import io.elephantchess.servicelayer.dto.lobby.GetUpcomingEventsResponse
import io.elephantchess.servicelayer.dto.lobby.GetUpcomingEventsResponse.UpcomingEvent
import io.elephantchess.servicelayer.dto.lobby.LatestGamesUpdateRequest
import io.elephantchess.servicelayer.dto.lobby.LatestGamesUpdateResponse
import io.elephantchess.servicelayer.services.ws.LiveGamesBatchUpdate
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
            // Lobby sessions watch mostly the same games, so fetch everything once: collect
            // all watched games and, per game, the lowest tracked move index across sessions
            // (so the single fetch covers every move any session may still need). Each session
            // then slices out only the moves it should actually receive.
            val requests = liveGamesSessions.map { session -> session.currentRequest() }
            val allGameIds = requests.flatMap { request -> request.gameIds }.distinct()

            if (allGameIds.isNotEmpty()) {
                val batchMoveIndexes = mutableMapOf<String, Int>()
                requests.forEach { request ->
                    request.moveIndexes.forEach { (gameId, index) ->
                        batchMoveIndexes.merge(gameId, index, ::minOf)
                    }
                }

                val request = LatestGamesUpdateRequest(allGameIds, batchMoveIndexes)
                val response = gameDataService.fetchLatestGamesUpdate(request)
                val batchUpdate = LiveGamesBatchUpdate(response, batchMoveIndexes)
                liveGamesSessions.forEach { session -> session.update(batchUpdate) }
            }

            // remove the sessions that are not active anymore
            liveGamesSessions.removeIf { session ->
                if (session.isClosed) logger.debug { "removing $session" }
                session.isClosed
            }
        }
    }

}
