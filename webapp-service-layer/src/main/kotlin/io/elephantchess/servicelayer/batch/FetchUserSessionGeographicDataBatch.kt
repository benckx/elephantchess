package io.elephantchess.servicelayer.batch

import io.elephantchess.db.model.UserSessionRecord
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.servicelayer.batch.definitions.ShardedBatch
import io.elephantchess.servicelayer.clients.ApiLayerClient
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.minutes

class FetchUserSessionGeographicDataBatch(
    private val userSessionDaoService: UserSessionDaoService,
    private val client: ApiLayerClient,
    override val logger: KLogger,
) :
    ShardedBatch<UserSessionRecord>() {

    override fun shardKey(element: UserSessionRecord) =
        element.remoteAddress

    override suspend fun fetchAll() =
        userSessionDaoService.listAllWithoutGeographicData(30.minutes)

    override suspend fun process(element: UserSessionRecord) {
        client
            .fetchGeographicData(element.remoteAddress)
            ?.let { locationResponse ->
                userSessionDaoService.updateGeographicData(
                    userId = element.userId,
                    remoteAddress = element.remoteAddress,
                    country = locationResponse.countryName,
                    countryCode = locationResponse.countryCode,
                    region = locationResponse.regionName,
                    city = locationResponse.city
                )
            }
    }

}
