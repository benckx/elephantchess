package io.elephantchess.servicelayer.dto.admin

data class PvpJoinSourceStatsResponse(
    val periods: List<String>,
    val percentageOver3: List<Double>,
    val percentageOver3LobbyJoinModes: List<Double>,
    val percentageOver3LinkJoinSource: List<Double>,
    val joinSourceBreakdown: List<TimeSeries>
)
