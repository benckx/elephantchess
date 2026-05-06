package io.elephantchess.servicelayer.dto.admin

data class TimeSeries(
    val name: String,
    val values: List<Number>,
)
