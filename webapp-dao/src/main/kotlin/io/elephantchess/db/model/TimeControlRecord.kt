package io.elephantchess.db.model

import io.elephantchess.model.TimeControlMode

data class TimeControlRecord(
    val mode: TimeControlMode,
    val base: Int,
    val increment: Int,
)
