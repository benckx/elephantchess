package io.elephantchess.servicelayer.dto.admin

data class PvpJoinSourceStatsResponse(
    val periods: List<String>,
    val percentageOver3: List<Double>,
    val joinSourceBreakdown: List<TimeSeries>
)
