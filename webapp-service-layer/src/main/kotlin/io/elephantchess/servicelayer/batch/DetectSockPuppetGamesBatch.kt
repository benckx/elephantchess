package io.elephantchess.servicelayer.batch

import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.SockPuppetReportDaoService
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.utils.di.KoinSingleton
import io.github.oshai.kotlinlogging.KLogger

@KoinSingleton
class DetectSockPuppetGamesBatch(
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    private val userSessionDaoService: UserSessionDaoService,
    private val sockPuppetReportDaoService: SockPuppetReportDaoService,
    override val logger: KLogger,
) : SinglePodBatch {

    override suspend fun run() {
        val candidates = pvpGameDaoService.listGamesPendingSockPuppetScan(SCAN_LIMIT_PER_RUN)
        if (candidates.isEmpty()) {
            return
        }

        val scannedGameIds = mutableListOf<String>()
        var flaggedGames = 0

        candidates.forEach { candidate ->
            try {
                val similarity = userSessionDaoService.countSharedSessionSimilarity(
                    candidate.inviterUserId,
                    candidate.inviteeUserId,
                )

                if (similarity.sharedFingerprintCount > 0) {
                    sockPuppetReportDaoService.upsertGameReport(
                        gameId = candidate.gameId,
                        inviterUserId = candidate.inviterUserId,
                        inviteeUserId = candidate.inviteeUserId,
                        sharedRemoteAddressCount = similarity.sharedRemoteAddressCount,
                        sharedFingerprintCount = similarity.sharedFingerprintCount,
                    )
                    flaggedGames += 1
                }

                scannedGameIds += candidate.gameId
            } catch (e: Exception) {
                logger.warn(e) { "error scanning game ${candidate.gameId} for sock puppet signal" }
            }
        }

        if (scannedGameIds.isNotEmpty()) {
            pvpGameDaoService.markGamesAsSockPuppetScanned(scannedGameIds)
        }

        logger.info { "sock puppet scan: scanned=${scannedGameIds.size}, flagged=$flaggedGames" }
    }

    private companion object {
        const val SCAN_LIMIT_PER_RUN = 500
    }
}
