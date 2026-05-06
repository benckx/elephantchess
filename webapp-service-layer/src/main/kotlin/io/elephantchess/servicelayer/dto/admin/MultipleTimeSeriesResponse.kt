package io.elephantchess.servicelayer.dto.admin

data class MultipleTimeSeriesResponse(
    val periods: List<String>,
    val timeSeries: List<TimeSeries>
)
