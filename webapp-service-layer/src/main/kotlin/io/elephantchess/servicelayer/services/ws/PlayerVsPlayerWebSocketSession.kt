package io.elephantchess.servicelayer.services.ws

import io.elephantchess.model.GameEventType
import io.elephantchess.servicelayer.dto.ws.PlayerVsPlayerUpdate
import io.elephantchess.servicelayer.model.UserId
import kotlinx.coroutines.channels.ChannelResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PlayerVsPlayerWebSocketSession(
    val gameId: String,
    val userId: UserId,
    private var status: GameEventType,
    private var moveIndex: Int,
    private var chatIndex: Int,
    private val sendCb: (PlayerVsPlayerUpdate) -> ChannelResult<Unit>,
) : WebSocketSession<PlayerVsPlayerUpdate>() {

    private var timeRemainingLastSync: Instant? = null
    private var drawPropositionUser: String? = null

    /**
     * Tracks the last [Instant] at which we sent a typing notification for each userId.
     * Used to avoid re-sending the same typing event on subsequent refresh cycles.
     */
    private val typingUserLastNotified: MutableMap<String, Instant> = mutableMapOf()

    fun currentIndex(): Int = moveIndex

    fun currentChatIndex(): Int = chatIndex

    fun currentStatus(): GameEventType = status

    fun isWaitingToBeJoined() =
        status == GameEventType.CREATED

    fun mustSyncTime(now: Instant): Boolean {
        return timeRemainingLastSync == null ||
                now > (timeRemainingLastSync!! + TIME_REMAINING_SYNC_INTERVAL)
    }

    /**
     * Returns the last [Instant] at which a typing notification for [userId] was sent to this
     * session, or null if it has never been sent.
     */
    fun getLastTypingNotified(userId: String): Instant? = typingUserLastNotified[userId]

    /**
     * Records that a typing notification for [userId] with the given [typedAt] timestamp was
     * sent to this session so that subsequent refresh cycles do not duplicate it.
     */
    fun markTypingNotified(userId: String, typedAt: Instant) {
        typingUserLastNotified[userId] = typedAt
    }

    override fun update(update: PlayerVsPlayerUpdate) {
        var mustUpdate = false

        // status changed
        if (update.status != null && update.status != status) {
            status = update.status
            drawPropositionUser = update.drawPropositionUser
            mustUpdate = true
        }

        // has joined
        if (isWaitingToBeJoined() && update.hasJoined != null) {
            mustUpdate = true
        }

        // new move
        if (update.newMove != null && update.newMove.updatedIndex > currentIndex()) {
            moveIndex = update.newMove.updatedIndex
            mustUpdate = true
        }

        // time remaining
        if (update.timeRemaining != null) {
            timeRemainingLastSync = Clock.System.now()
            mustUpdate = true
        }

        if (update.chatMessages.isNotEmpty()) {
            chatIndex = update.chatMessages.maxOf { it.index } + 1
            mustUpdate = true
        }

        if (update.typingUsers.isNotEmpty()) {
            mustUpdate = true
        }

        // push update
        if (mustUpdate) {
            val result = sendCb(update)
            if (result.isClosed) {
                markAsClosed()
            } else if (result.isFailure) {
                logger.error { "failed to send data to player vs player session $sessionId" }
            }
        }
    }

    override fun toString() =
        "${javaClass.simpleName}{sessionId=$sessionId, gameId=$gameId}"

    private companion object {

        val TIME_REMAINING_SYNC_INTERVAL = 20.seconds

    }

}
