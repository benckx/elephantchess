package io.elephantchess.db.model

import io.elephantchess.model.TimeControlCategory
import io.elephantchess.xiangqi.Variant

data class UserRatingSummaryRecord(
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
