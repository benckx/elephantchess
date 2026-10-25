package io.elephantchess.servicelayer.batch

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
        pvpGameDaoService.listGamesPendingSockPuppetScan(SCAN_LIMIT_PER_RUN)

    override suspend fun process(element: SockPuppetScanCandidateRecord) {
        val similarity = userSessionDaoService.countSharedSessionSimilarity(
            element.inviterUserId,
            element.inviteeUserId,
        )

        if (similarity.sharedFingerprintCount > 0) {
            sockPuppetReportDaoService.upsertGameReport(
                gameId = element.gameId,
                inviterUserId = element.inviterUserId,
                inviteeUserId = element.inviteeUserId,
                sharedRemoteAddressCount = similarity.sharedRemoteAddressCount,
                sharedFingerprintCount = similarity.sharedFingerprintCount,
            )
        }

        pvpGameDaoService.markGamesAsSockPuppetScanned(listOf(element.gameId))
    }

    private companion object {
        const val SCAN_LIMIT_PER_RUN = 500
    }
}
