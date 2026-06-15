package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.UserDaoService
import io.elephantchess.model.TimeControlCategory
import io.elephantchess.servicelayer.dto.admin.UserEloStatsResponse
import io.elephantchess.xiangqi.Variant

class AdminUserEloService(private val userDaoService: UserDaoService) {

    suspend fun listUserEloStats(): UserEloStatsResponse {
        val entries =
            Variant.entries.flatMap { variant ->
                TimeControlCategory.entries.map { timeControlCategory ->
                    userDaoService.fetchRatingSummary(timeControlCategory, variant).let { record ->
                        UserEloStatsResponse.Entry(
                            variant = record.variant,
                            timeControlCategory = record.timeControlCategory,
                            userCount = record.userCount,
                            averageRating = record.averageRating,
                            minUserId = record.minUserId,
                            minUsername = record.minUsername,
                            minRating = record.minRating,
                            maxUserId = record.maxUserId,
                            maxUsername = record.maxUsername,
                            maxRating = record.maxRating,
                        )
                    }
                }
            }

        return UserEloStatsResponse(entries)
    }

}
