package io.elephantchess.scripts.openings

import io.elephantchess.model.Outcome
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.RED

internal data class ReferenceGameDto(
    val id: String,
    val outcome: Outcome,
    val redPlayer: String?,
    val blackPlayer: String?,
    val moves: List<String>,
) {
    fun playerColor(playerId: String): Color? =
        when (playerId) {
            redPlayer -> RED
            blackPlayer -> BLACK
            else -> null
        }
}
