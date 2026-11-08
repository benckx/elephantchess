package io.elephantchess.db.callback

import io.elephantchess.model.GameEventType
import io.elephantchess.model.GameEventType.CHECKMATED
import io.elephantchess.model.GameEventType.STALEMATED
import io.elephantchess.model.Outcome
import io.elephantchess.model.Outcome.BLACK_WINS
import io.elephantchess.model.Outcome.WHITE_WINS
import io.elephantchess.xiangqi.Color

data class PlayMoveBotGameCallbackResult(
    val botMove: BotMove? = null,
    val newFen: String? = null,
    val newPosition: Int? = null,
    val gameEventType: GameEventType? = null,
    val outcome: Outcome? = null,
    val botVictory: Boolean = false,
    val userVictory: Boolean = false,
) {

    private fun updateWinningColor(color: Color): PlayMoveBotGameCallbackResult =
        if (color == Color.WHITE) {
            copy(outcome = WHITE_WINS)
        } else {
            copy(outcome = BLACK_WINS)
        }

    private fun updateForMate(checkmated: Boolean, stalemated: Boolean): PlayMoveBotGameCallbackResult =
        if (checkmated) {
            copy(gameEventType = CHECKMATED)
        } else if (stalemated) {
            copy(gameEventType = STALEMATED)
        } else {
            this
        }

    private fun updateWinner(color: Color, checkmated: Boolean, stalemated: Boolean): PlayMoveBotGameCallbackResult =
        updateWinningColor(color).updateForMate(checkmated, stalemated)

    fun updateForBotVictory(color: Color, checkmated: Boolean, stalemated: Boolean): PlayMoveBotGameCallbackResult =
        updateWinner(color, checkmated, stalemated).copy(botVictory = true)

    fun updateForUserVictory(color: Color, checkmated: Boolean, stalemated: Boolean): PlayMoveBotGameCallbackResult =
        updateWinner(color, checkmated, stalemated).copy(userVictory = true)
}
