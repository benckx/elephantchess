package io.elephantchess.db.callback

import io.elephantchess.model.GameEventType
import io.elephantchess.model.Outcome

data class PerpetualCheckingCallbackResult(
    val newGameEventType: GameEventType,
    val outcome: Outcome,
)
