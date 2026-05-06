package io.elephantchess.servicelayer.dto.global

import io.elephantchess.model.TimeControlCategory

data class GlobalPlayerVsPlayerLeaderboardResponse(val entries: List<Entry>) {

    data class Entry(
        val category: TimeControlCategory,
        val userId: String,
        val username: String,
        val countryCode: String?,
        val rating: Int,
        val totalPlayed: Int,
        val lastPlayed: Long
    )

}
