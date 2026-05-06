package io.elephantchess.servicelayer.services.ws

import io.elephantchess.servicelayer.dto.ws.BotGameSpectatorUpdate
import kotlinx.coroutines.channels.ChannelResult

class PvbWebSocketSession(
    private val sendCb: (BotGameSpectatorUpdate) -> ChannelResult<Unit>,
    val gameId: String,
    private var moveIndex: Int,
) : WebSocketSession<BotGameSpectatorUpdate>() {

    val currentMoveIndex
        get() = moveIndex

    override fun update(update: BotGameSpectatorUpdate) {
        if (update.moveIndex > moveIndex && update.newMoves.isNotEmpty()) {
            val result = sendCb(update)
            if (result.isSuccess) {
                moveIndex = update.moveIndex
            } else if (result.isClosed) {
                markAsClosed()
            } else if (result.isFailure) {
                logger.error { "failed to send data to spectator session $sessionId" }
            }
        }
    }

    override fun toString() =
        "${javaClass.simpleName}{sessionId=$sessionId, gameId=$gameId}"

}
