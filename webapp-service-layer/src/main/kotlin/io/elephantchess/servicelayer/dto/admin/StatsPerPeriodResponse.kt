package io.elephantchess.servicelayer.dto.admin

data class StatsPerPeriodResponse(val entries: List<Entry>) {

    data class Entry(val period: String, val count: Number)

}
