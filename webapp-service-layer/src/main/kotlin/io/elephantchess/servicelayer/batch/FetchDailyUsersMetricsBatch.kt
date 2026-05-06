package io.elephantchess.servicelayer.batch

import io.elephantchess.db.dao.codegen.tables.pojos.StatsOnlineUsersDays
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.services.UserStatsDaoService
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.servicelayer.utils.userIdsExcludedFromAnalytics
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.days

class FetchDailyUsersMetricsBatch(
    private val userDaoService: UserDaoService,
    private val userStatsDaoService: UserStatsDaoService,
    override val logger: KLogger,
) : SinglePodBatch {

    override val podNumber: Int = 0

    override suspend fun run() {
        val record = StatsOnlineUsersDays()

        record.authenticatedUsers_7d =
            userDaoService.countActiveRecently(
                duration = 7.days,
                userTypes = listOf(UserType.AUTHENTICATED),
                excludeIds = userIdsExcludedFromAnalytics
            )

        record.authenticatedUsers_30d =
            userDaoService.countActiveRecently(
                duration = 30.days,
                userTypes = listOf(UserType.AUTHENTICATED),
                excludeIds = userIdsExcludedFromAnalytics
            )

        record.guestsUsers_7d =
            userDaoService.countActiveRecently(
                duration = 7.days,
                userTypes = listOf(UserType.GUEST),
                excludeIds = userIdsExcludedFromAnalytics
            )

        record.guestsUsers_30d =
            userDaoService.countActiveRecently(
                duration = 30.days,
                userTypes = listOf(UserType.GUEST),
                excludeIds = userIdsExcludedFromAnalytics
            )

        userStatsDaoService.save(record)
    }

}
