package io.elephantchess.model

enum class BotGameMoveType {

    /**
     * The move was played by the user
     */
    USER,

    /**
     * The bot played with the engine
     */
    ENGINE,

    /**
     * The bot played a move from the opening repository
     */
    OPENING

}
