package io.elephantchess.model

enum class GameType {

    /**
     * Game between two players
     */
    PVP,

    /**
     * Game between a player and a bot
     */
    PVB,

    /**
     * Game from tournaments database
     */
    DB

}
