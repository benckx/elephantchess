package io.elephantchess.servicelayer.dto.gamedata

data class GameMovesResponse(
    val moves: List<String>,
    /**
     * Wall-clock timestamps (epoch milliseconds) for each move, parallel to [moves].
     * Only populated for PvP games to allow client-side timer/chat reconstruction when
     * navigating the move history. Null for game types that don't track move timestamps.
     */
    val moveTimestamps: List<Long>? = null,
    /**
     * Wall-clock timestamp (epoch milliseconds) at which the game started (invitee joined).
     * Only populated for PvP games. Null otherwise.
     */
    val joinTime: Long? = null,
)
