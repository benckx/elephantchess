package io.elephantchess.servicelayer.dto.ws

import io.elephantchess.model.GameId

/**
 * Sent by a lobby client over the live-games WebSocket to declare which games it
 * is currently displaying and wants to receive updates for. The move index per
 * game is tracked server-side (in [io.elephantchess.servicelayer.services.ws.LiveGamesWebSocketSession]),
 * so the client only needs to send the game ids.
 */
data class LiveGamesSubscription(
    val gameIds: List<GameId> = emptyList()
)
