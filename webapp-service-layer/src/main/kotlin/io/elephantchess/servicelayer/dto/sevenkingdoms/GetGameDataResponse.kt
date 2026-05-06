package io.elephantchess.servicelayer.dto.sevenkingdoms

import io.elephantchess.model.GameEventType
import io.elephantchess.sevenkingdoms.Color

data class GetGameDataResponse(
    val whitePlayer: Player?,
    val redPlayer: Player?,
    val orangePlayer: Player?,
    val bluePlayer: Player?,
    val greenPlayer: Player?,
    val purplePlayer: Player?,
    val blackPlayer: Player?,
    val winner: Player?,
    val status: GameEventType,
    val currentIndex: Int,
    val currentFen: String,
    val colorToPlay: Color?
) {

    data class Player(
        val userId: String,
        val userName: String
    )

}
