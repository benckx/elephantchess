package io.elephantchess.servicelayer.dto.database

/**
 * Lightweight summary of a database (reference) game, used to populate
 * the title / meta description of the database game viewer page.
 */
data class DatabaseGameSummary(
    val gameId: String,
    val redPlayerName: String?,
    val blackPlayerName: String?,
    val eventName: String?,
)

