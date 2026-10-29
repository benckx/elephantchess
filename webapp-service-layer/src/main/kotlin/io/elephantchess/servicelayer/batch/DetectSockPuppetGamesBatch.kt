package io.elephantchess.servicelayer.batch

import io.elephantchess.db.dao.codegen.tables.pojos.GameSockPuppetDetection
import io.elephantchess.db.model.SockPuppetScanCandidateRecord
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.SockPuppetReportDaoService
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.servicelayer.batch.definitions.ShardedBatch
import io.elephantchess.utils.di.KoinSingleton
import io.github.oshai.kotlinlogging.KLogger

@KoinSingleton
class DetectSockPuppetGamesBatch(
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    private val userSessionDaoService: UserSessionDaoService,
    private val sockPuppetReportDaoService: SockPuppetReportDaoService,
    override val logger: KLogger,
) : ShardedBatch<SockPuppetScanCandidateRecord>() {

    override fun shardKey(element: SockPuppetScanCandidateRecord): String =
        element.gameId

    override suspend fun fetchAll(): List<SockPuppetScanCandidateRecord> =
        pvpGameDaoService.listGamesPendingSockPuppetScan()

    override suspend fun process(element: SockPuppetScanCandidateRecord) {
        val similarity = userSessionDaoService.countSharedSessionSimilarity(
            element.inviterUserId,
            element.inviteeUserId,
        )

        if (similarity.sharedFingerprintCount > 0) {
            sockPuppetReportDaoService.insert(
                GameSockPuppetDetection().apply {
                    gameId = element.gameId
                    inviterUserId = element.inviterUserId
                    inviteeUserId = element.inviteeUserId
                    sharedRemoteAddressCount = similarity.sharedRemoteAddressCount
                    sharedFingerprintCount = similarity.sharedFingerprintCount
                    sharedRemoteAddress = similarity.sharedRemoteAddress
                    sharedCountryCode = similarity.sharedCountryCode
                    sharedCountryName = similarity.sharedCountryName
                    sharedRegion = similarity.sharedRegion
                    sharedCity = similarity.sharedCity
                    sharedOperatingSystemName = similarity.sharedOperatingSystemName
                    sharedAgentName = similarity.sharedAgentName
                    sharedAgentClass = similarity.sharedAgentClass
                    sharedUserAgent = similarity.sharedUserAgent
                },
            )

            logger.info {
                "game ${element.gameId} detected as sock puppet for ${similarity.sharedRemoteAddressCount} shared report addresses " +
                    "and ${similarity.sharedFingerprintCount} shared fingerprints"
            }
        }

        pvpGameDaoService.markGamesAsSockPuppetScanned(listOf(element.gameId))
    }
}
