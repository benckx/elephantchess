package io.elephantchess.servicelayer.dto.analysis

import io.elephantchess.xiangqi.Color

/**
 * Request for the opening repertoire of a single reference (database) player.
 *
 * @param color the technical side the player played, or `null` for "all".
 */
data class OpeningReferencePlayerNextMovesRequest(
    val moves: List<String>,
    val playerId: String,
    val color: Color? = null,
)
