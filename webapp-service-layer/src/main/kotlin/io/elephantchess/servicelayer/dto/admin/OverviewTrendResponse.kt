package io.elephantchess.servicelayer.dto.admin

data class OverviewTrendResponse(val gamesOver3: TrendValue, val puzzles: TrendValue) {
    data class TrendValue(val latest30Days: Int, val previous30Days: Int)
}
