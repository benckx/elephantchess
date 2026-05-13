package io.elephantchess.db.model

import io.elephantchess.xiangqi.Color
import java.time.LocalDate
import kotlin.time.Instant

data class UserDbSearchEntry(
    val queryId: String,
    val queryTime: Instant,
    val updateTime: Instant,
    val playerName: String?,
    val playerId: String?,
    val playerColor: Color?,
    val eventName: String?,
    val eventId: String?,
    val searchStart: LocalDate?,
    val searchEnd: LocalDate?,
    val fen: String?,
    val numberOfResults: Int,
)
