package io.elephantchess.servicelayer.dto.gamedata

import io.elephantchess.model.*
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Variant

data class GameMetadataDto(
    val gameId: GameId,
    val redPlayerName: String? = null,
    val redPlayerId: String? = null,
    val redPlayerRating: Int? = null,
    val redUserType: UserType? = null,
    val isRedOnline: Boolean? = null,
    val blackPlayerName: String? = null,
    val blackPlayerId: String? = null,
    val blackPlayerRating: Int? = null,
    val blackUserType: UserType? = null,
    val isBlackOnline: Boolean? = null,
    val userColor: Color? = null,
    val eventId: String? = null,
    val eventName: String? = null,
    val startFen: String? = null,
    val finalFen: String,
    val status: GameEventType? = null,
    val outcome: Outcome? = null,
    val analysisStatus: AnalysisStatus? = null,
    val engine: Engine? = null,
    val depth: Int? = null,
    val lastUpdated: Long? = null,
    val paginationOffset: Int? = null,
    val variant: Variant
)
