package io.elephantchess.db.model

import io.elephantchess.model.GameEventType
import io.elephantchess.xiangqi.Color

data class BotGameStatusRecord(
    val userId: String?,
    val userColor: Color,
    val status: GameEventType,
    val position: Int,
) {

    fun isInProgress(): Boolean {
        return status.isInProgress() || status == GameEventType.CREATED
    }

    fun canCancel(): Boolean {
        return isInProgress() && !userHasPlayed()
    }

    private fun userHasPlayed(): Boolean {
        return (position > 0 && userColor == Color.RED) || (position > 1 && userColor == Color.BLACK)
    }

}
