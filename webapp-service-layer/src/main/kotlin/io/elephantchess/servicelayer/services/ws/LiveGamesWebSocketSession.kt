package io.elephantchess.servicelayer.services.ws

import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameId
import io.elephantchess.servicelayer.dto.lobby.LatestGamesUpdateRequest
import io.elephantchess.servicelayer.dto.lobby.LatestGamesUpdateResponse
import kotlinx.coroutines.channels.ChannelResult

/**
 * Batched update handed to every live games session: the [response] is fetched once
 * for the union of all watched games (using the lowest tracked move index per game,
 * see [batchMoveIndexes]) so each session can slice out only the moves it still needs.
 */
data class LiveGamesBatchUpdate(
    val response: LatestGamesUpdateResponse,
    val batchMoveIndexes: Map<String, Int>,
)

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
) : WebSocketSession<LiveGamesBatchUpdate>() {

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

    override fun update(update: LiveGamesBatchUpdate) {
        val watchedIds = subscribedGameIds.map { it.id }.toSet()
        val entries = update.response.entries
            .filter { entry -> entry.gameId.id in watchedIds }
            .map { entry -> sliceForSession(entry, update.batchMoveIndexes) }

        val changedEntries = entries.filter { entry ->
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
        entries.forEach { entry ->
            entry.moveIndex?.let { moveIndexes[entry.gameId.id] = it }
            lastStatuses[entry.gameId.id] = entry.status
        }
    }

    /**
     * The batched moves start at [LiveGamesBatchUpdate.batchMoveIndexes] (the lowest index
     * any session needed), so drop the moves this session has already seen. On the first
     * update for a game (no tracked index yet) no moves are animated: the client loads the fen.
     */
    private fun sliceForSession(
        entry: LatestGamesUpdateResponse.Entry,
        batchMoveIndexes: Map<String, Int>,
    ): LatestGamesUpdateResponse.Entry {
        val tracked = moveIndexes[entry.gameId.id]
        val batchFrom = batchMoveIndexes[entry.gameId.id]
        val newMoves = when {
            tracked == null -> emptyList()
            batchFrom == null -> entry.newMoves
            else -> entry.newMoves.drop(maxOf(0, tracked - batchFrom))
        }
        return entry.copy(newMoves = newMoves)
    }

    override fun toString() =
        "${javaClass.simpleName}{sessionId=$sessionId, games=${subscribedGameIds.size}}"

}
