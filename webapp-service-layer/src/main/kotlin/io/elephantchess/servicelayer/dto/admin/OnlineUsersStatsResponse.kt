package io.elephantchess.servicelayer.dto.admin

data class OnlineUsersStatsByHourResponse(
    val entries: List<Entry>
) {
    data class Entry(
        val hourOfDay: Int,
        val minTotal: Int,
        val maxTotal: Int,
        val avgTotal: Int
    )
}

data class OnlineUsersStatsByDayResponse(
    val entries: List<Entry>
) {
    data class Entry(
        val day: String, // ISO date format YYYY-MM-DD
        val minTotal: Int,
        val maxTotal: Int,
        val avgTotal: Int
    )
}

data class OnlineUsersStatsByDayOfWeekResponse(
    val entries: List<Entry>
) {
    data class Entry(
        val dayOfWeek: Int, // 0 = Sunday, 1 = Monday, etc.
        val minTotal: Int,
        val maxTotal: Int,
        val avgTotal: Int
    )
}

data class OnlineUsersStatsByMonthResponse(
    val entries: List<Entry>
) {
    data class Entry(
        val month: String, // ISO format YYYY-MM
        val minTotal: Int,
        val maxTotal: Int,
        val avgTotal: Int
    )
}
