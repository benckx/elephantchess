package io.elephantchess.db.callback

import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameEventType.CHECKMATED
import io.elephantchess.model.GameEventType.STALEMATED
import io.elephantchess.model.Outcome

data class PlayMoveCallbackResult(
    val newFen: String,
    val newPosition: Int,
    val newGameEventType: GameEventType? = null,
    val outcome: Outcome? = null,
    val mustCheckPerpetualChecking: Boolean = false,
) {

    fun flagInCheck(): PlayMoveCallbackResult {
        return copy(mustCheckPerpetualChecking = true)
    }

    fun isMated(): Boolean {
        return outcome != null && (newGameEventType == CHECKMATED || newGameEventType == STALEMATED)
    }

}
