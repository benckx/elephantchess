package io.elephantchess.model

enum class GameEventType {

    CREATED,
    CANCELED,
    AUTO_CANCELED,
    JOINED,
    RESIGNED,
    AUTO_RESIGNED,
    DRAW_PROPOSED,
    DRAW_ACCEPTED,
    DRAW_DECLINED,
    CHECKMATED,
    STALEMATED,
    OTHER_VICTORY,
    FLAGGED,
    PERPETUAL_CHECKING;

    fun isInProgress(): Boolean {
        return this in inProgressStatuses
    }

    /**
     * The game has started and has now ended, so doesn't include cancelled games
     */
    fun hasEnded(): Boolean {
        return this in gameEndedStatuses
    }

    companion object {

        val inProgressStatuses =
            listOf(
                JOINED,
                DRAW_PROPOSED,
                DRAW_DECLINED
            )

        val gameEndedStatuses =
            listOf(
                DRAW_ACCEPTED,
                CHECKMATED,
                STALEMATED,
                OTHER_VICTORY,
                RESIGNED,
                AUTO_RESIGNED,
                FLAGGED,
                PERPETUAL_CHECKING
            )

    }

}
