package io.elephantchess.db.model

import kotlin.time.Instant

data class TypingStatusEntry(
    val userId: String,
    val typedAt: Instant
)
