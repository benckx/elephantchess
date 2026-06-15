package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.UserDaoService
import io.elephantchess.model.TimeControlCategory
import io.elephantchess.model.UserType
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.model.UserType.GUEST
import io.elephantchess.servicelayer.dto.admin.UserEloStatsResponse
import io.elephantchess.xiangqi.Variant

class AdminUserEloService(private val userDaoService: UserDaoService) {

    suspend fun listUserEloStats(): UserEloStatsResponse {
        return UserEloStatsResponse(
            authenticatedEntries = listEntries(AUTHENTICATED),
            guestEntries = listEntries(GUEST)
        )
    }

    private suspend fun listEntries(userType: UserType): List<UserEloStatsResponse.Entry> {
        return Variant.entries.flatMap { variant ->
            TimeControlCategory.entries.map { timeControlCategory ->
                userDaoService.fetchRatingSummary(timeControlCategory, variant, userType).let { record ->
                    UserEloStatsResponse.Entry(
                        variant = variant,
                        timeControlCategory = timeControlCategory,
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
    }

}
