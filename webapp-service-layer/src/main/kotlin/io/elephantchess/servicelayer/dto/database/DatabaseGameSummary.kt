package io.elephantchess.servicelayer.dto.database

import io.elephantchess.model.Outcome
import java.time.LocalDate

/**
 * Lightweight summary of a database (reference) game, used to populate
 * the title / meta description / server-rendered content of the database
 * game viewer page.
 */
data class DatabaseGameSummary(
    val gameId: String,
    val redPlayerCanonicalName: String?,
    val redPlayerChineseName: String?,
    val blackPlayerCanonicalName: String?,
    val blackPlayerChineseName: String?,
    val eventId: String?,
    val eventName: String?,
    val date: LocalDate?,
    val outcome: Outcome?,
)
