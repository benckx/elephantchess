package io.elephantchess.servicelayer.services.ws

import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.ws.GamesToPlayUpdate
import io.elephantchess.servicelayer.model.UserId
import kotlinx.coroutines.channels.ChannelResult

class GamesToPlayWebSocketSession(
    private val subscriber: UserId,
    private val sendCb: (GamesToPlayUpdate) -> ChannelResult<Unit>
) : WebSocketSession<GamesToPlayUpdate>() {

    private val currentGameIdsToJoin = mutableSetOf<String>()
    private val currentTurnToPlayGames = mutableSetOf<String>()
    private var totalOnline = 0
    private var lastSentAt = 0L

    val userType: UserType
        get() = subscriber.userType

    val userId: String
        get() = subscriber.id

    override fun update(update: GamesToPlayUpdate) {
        val gameToJoinIds = update.gamesToJoin.map { it.gameId }.toSet()
        val turnToPlayGames = update.turnToPlayGames.map { it.gameId }.toSet()
        val now = System.currentTimeMillis()

        if (
            totalOnline != update.totalOnline ||
            gameToJoinIds != currentGameIdsToJoin ||
            turnToPlayGames != currentTurnToPlayGames ||
            now - lastSentAt >= FORCE_REFRESH_INTERVAL_MS
        ) {
            val result = sendCb(update)
            if (result.isClosed) {
                markAsClosed()
            } else if (result.isFailure) {
                logger.error { "failed to send data to games to play session $sessionId" }
            }

            currentGameIdsToJoin.clear()
            currentGameIdsToJoin.addAll(gameToJoinIds)
            currentTurnToPlayGames.clear()
            currentTurnToPlayGames.addAll(turnToPlayGames)
            totalOnline = update.totalOnline
            lastSentAt = now
        }
    }

    override fun toString() =
        "${javaClass.simpleName}{sessionId=$sessionId, subscriber=$subscriber}"

    companion object {
        private const val FORCE_REFRESH_INTERVAL_MS = 30_000L
    }

}
