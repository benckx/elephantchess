package io.elephantchess.servicelayer.services.ws

import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameId
import io.elephantchess.servicelayer.dto.lobby.LatestGamesUpdateRequest
import io.elephantchess.servicelayer.dto.lobby.LatestGamesUpdateResponse
import kotlinx.coroutines.channels.ChannelResult

/**
 * WebSocket session for a lobby client watching the live games thumbnails.
 *
 * The client declares which games it watches via [updateSubscription]; the last
 * known move index per game is tracked here (not sent by the client), so the
 * refresher can request only the new moves and the session only pushes entries
 * that actually changed (new moves or a status change).
 */
class LiveGamesWebSocketSession(
    private val sendCb: (LatestGamesUpdateResponse) -> ChannelResult<Unit>,
) : WebSocketSession<LatestGamesUpdateResponse>() {

    private var subscribedGameIds: List<GameId> = emptyList()
    private val moveIndexes = mutableMapOf<String, Int>()
    private val lastStatuses = mutableMapOf<String, GameEventType>()

    fun updateSubscription(gameIds: List<GameId>) {
        subscribedGameIds = gameIds

        // forget tracking for games that are not watched anymore
        val watchedIds = gameIds.map { it.id }.toSet()
        moveIndexes.keys.retainAll(watchedIds)
        lastStatuses.keys.retainAll(watchedIds)
    }

    fun currentRequest(): LatestGamesUpdateRequest =
        LatestGamesUpdateRequest(
            gameIds = subscribedGameIds,
            moveIndexes = moveIndexes.toMap()
        )

    override fun update(update: LatestGamesUpdateResponse) {
        val changedEntries = update.entries.filter { entry ->
            val id = entry.gameId.id
            val firstTime = id !in lastStatuses
            val statusChanged = lastStatuses[id] != entry.status
            firstTime || statusChanged || entry.newMoves.isNotEmpty()
        }

        if (changedEntries.isNotEmpty()) {
            val result = sendCb(LatestGamesUpdateResponse(changedEntries))
            if (result.isClosed) {
                markAsClosed()
                return
            } else if (result.isFailure) {
                logger.error { "failed to send data to live games session $sessionId" }
                return
            }
        }

        // advance the tracked indexes/statuses so the next request only asks for newer moves
        update.entries.forEach { entry ->
            entry.moveIndex?.let { moveIndexes[entry.gameId.id] = it }
            lastStatuses[entry.gameId.id] = entry.status
        }
    }

    override fun toString() =
        "${javaClass.simpleName}{sessionId=$sessionId, games=${subscribedGameIds.size}}"

}
