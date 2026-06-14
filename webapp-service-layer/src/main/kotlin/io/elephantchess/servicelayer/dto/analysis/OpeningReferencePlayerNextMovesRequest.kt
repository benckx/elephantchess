package io.elephantchess.servicelayer.dto.analysis

/**
 * Request for the opening repertoire of a single reference (database) player.
 *
 * @param color the color the player played ("RED" / "BLACK"), or `null` for "all".
 */
data class OpeningReferencePlayerNextMovesRequest(
    val moves: List<String>,
    val playerId: String,
    val color: String? = null,
)
