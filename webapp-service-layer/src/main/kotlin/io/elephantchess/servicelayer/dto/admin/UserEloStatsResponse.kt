package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.TimeControlCategory
import io.elephantchess.xiangqi.Variant

data class UserEloStatsResponse(
    val authenticatedEntries: List<Entry>,
    val guestEntries: List<Entry>,
) {

    data class Entry(
        val variant: Variant,
        val timeControlCategory: TimeControlCategory,
        val userCount: Int,
        val averageRating: Double?,
        val minUserId: String?,
        val minUsername: String?,
        val minRating: Int?,
        val maxUserId: String?,
        val maxUsername: String?,
        val maxRating: Int?,
    )

}
