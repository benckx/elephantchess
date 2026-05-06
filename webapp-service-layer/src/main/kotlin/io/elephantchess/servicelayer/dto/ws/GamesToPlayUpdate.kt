package io.elephantchess.servicelayer.dto.ws

data class GamesToPlayUpdate(
    val gamesToJoin: List<GameToPlay>,
    val turnToPlayGames: List<GameToPlay>,
    val totalOnline: Int
)
