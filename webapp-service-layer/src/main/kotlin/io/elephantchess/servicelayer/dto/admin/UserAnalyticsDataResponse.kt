package io.elephantchess.servicelayer.dto.admin

data class UserAnalyticsDataResponse(val entries: List<Entry>) {

    data class Entry(
        val userId: String,
        val username: String?,
        val email: String?,
        val puzzleRating: Int,
        val numberOfDaysSinceMember: Int,
        val numberOfDaysSinceLastOnline: Int?,
        val numberOfPlayedPuzzles: Int,
        val numberOfDaysPlayedPuzzles: Int
    )

}
