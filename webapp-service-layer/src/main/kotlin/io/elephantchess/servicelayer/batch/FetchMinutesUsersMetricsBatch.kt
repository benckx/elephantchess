package io.elephantchess.servicelayer.batch

import io.elephantchess.db.dao.codegen.tables.pojos.StatsOnlineUsersMinutes
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.services.UserStatsDaoService
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.servicelayer.utils.userIdsExcludedFromAnalytics
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class FetchMinutesUsersMetricsBatch(
    private val userDaoService: UserDaoService,
    private val userStatsDaoService: UserStatsDaoService,
    override val logger: KLogger,
) : SinglePodBatch {

    override val podNumber: Int = 0

    override suspend fun run() {
        val record = StatsOnlineUsersMinutes()

        record.authenticatedUsers_5min =
            userDaoService.countActiveRecently(
                duration = 5.minutes,
                userTypes = listOf(UserType.AUTHENTICATED),
                excludeIds = userIdsExcludedFromAnalytics
            )

        record.authenticatedUsers_1h =
            userDaoService.countActiveRecently(
                duration = 1.hours,
                userTypes = listOf(UserType.AUTHENTICATED),
                excludeIds = userIdsExcludedFromAnalytics
            )

        record.authenticatedUsers_24h =
            userDaoService.countActiveRecently(
                duration = 24.hours,
                userTypes = listOf(UserType.AUTHENTICATED),
                excludeIds = userIdsExcludedFromAnalytics
            )

        record.guestsUsers_5min =
            userDaoService.countActiveRecently(
                duration = 5.minutes,
                userTypes = listOf(UserType.GUEST),
                excludeIds = userIdsExcludedFromAnalytics
            )

        record.guestsUsers_1h =
            userDaoService.countActiveRecently(
                duration = 1.hours,
                userTypes = listOf(UserType.GUEST),
                excludeIds = userIdsExcludedFromAnalytics
            )

        record.guestsUsers_24h =
            userDaoService.countActiveRecently(
                duration = 24.hours,
                userTypes = listOf(UserType.GUEST),
                excludeIds = userIdsExcludedFromAnalytics
            )

        userStatsDaoService.save(record)
    }

}
