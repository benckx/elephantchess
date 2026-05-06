package io.elephantchess.db.model

import java.time.LocalDate

data class EventListEntryRecord(
    val id: String,
    val name: String,
    val date: LocalDate?,
    val maxRound: Int?,
    val gameCount: Int
)
