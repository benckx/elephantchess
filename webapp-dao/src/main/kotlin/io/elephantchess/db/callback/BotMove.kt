package io.elephantchess.db.callback

import io.elephantchess.model.BotGameMoveType

data class BotMove(val uci: String, val moveType: BotGameMoveType)
