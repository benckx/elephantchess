package io.elephantchess.servicelayer.batch

import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.tables.pojos.StatsOnlineUsersDays
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.services.UserStatsDaoService
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.days

class FetchDailyUsersMetricsBatch(
    private val userDaoService: UserDaoService,
    private val userStatsDaoService: UserStatsDaoService,
    appConfig: AppConfig,
    override val logger: KLogger,
) : SinglePodBatch {

    override val podNumber: Int = 0
    private val excludedIds = appConfig.loadListOfStrings("excluded.from.analytics")

    override suspend fun run() {
        val record = StatsOnlineUsersDays()

        record.authenticatedUsers_7d =
            userDaoService.countActiveRecently(
                duration = 7.days,
                userTypes = listOf(UserType.AUTHENTICATED),
                excludeIds = excludedIds
            )

        record.authenticatedUsers_30d =
            userDaoService.countActiveRecently(
                duration = 30.days,
                userTypes = listOf(UserType.AUTHENTICATED),
                excludeIds = excludedIds
            )

        record.guestsUsers_7d =
            userDaoService.countActiveRecently(
                duration = 7.days,
                userTypes = listOf(UserType.GUEST),
                excludeIds = excludedIds
            )

        record.guestsUsers_30d =
            userDaoService.countActiveRecently(
                duration = 30.days,
                userTypes = listOf(UserType.GUEST),
                excludeIds = excludedIds
            )

        userStatsDaoService.save(record)
    }

}
