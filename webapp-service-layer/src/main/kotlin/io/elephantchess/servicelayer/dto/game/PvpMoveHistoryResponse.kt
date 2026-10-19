package io.elephantchess.servicelayer.dto.game

/**
 * One entry in the PvP move history: a UCI move together with the wall-clock
 * timestamp (epoch milliseconds) at which the move was played.
 */
data class GameMoveEntry(
    val move: String,
    val timestamp: Long,
)

/**
 * Response for the PvP `/api/game/moves-history` endpoint.
 *
 * Unlike the generic [io.elephantchess.servicelayer.dto.gamedata.GameMovesResponse]
 * used by the analysis board, this carries the per-move timestamps and the game's
 * start time so the client can reconstruct the clock and anchor chat messages when
 * the user navigates back through the move tree.
 */
data class PvpMoveHistoryResponse(
    val moves: List<GameMoveEntry>,
    /**
     * Wall-clock timestamp (epoch milliseconds) at which the invitee joined and the
     * game started. Null if no one has joined yet (in which case [moves] is empty).
     */
    val joinTime: Long?,
)
