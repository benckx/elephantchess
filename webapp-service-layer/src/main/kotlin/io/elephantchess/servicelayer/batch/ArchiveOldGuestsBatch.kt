package io.elephantchess.servicelayer.batch

import io.elephantchess.utils.di.KoinSingleton

import io.elephantchess.db.services.ArchivedGuestDaoService
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.servicelayer.services.analytics.ARCHIVE_GUEST_AFTER_DAYS
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.days

/**
 * Archives inactive guest users (older than [ARCHIVE_GUEST_AFTER_DAYS] days, no PvP/PvB/puzzle activity)
 * into aggregated daily tables and deletes them together with their page views, database searches and
 * sessions.
 *
 * Guests are processed in bounded chunks so a single run stays cheap; the schedule drains the backlog
 * over multiple runs.
 */
@KoinSingleton
class ArchiveOldGuestsBatch(
    private val archivedGuestDaoService: ArchivedGuestDaoService,
    override val logger: KLogger,
) : SinglePodBatch {

    override val podNumber: Int = 1

    override suspend fun run() {
        val guestIds = archivedGuestDaoService.selectArchivableGuestIds(
            maxAge = ARCHIVE_GUEST_AFTER_DAYS.days,
            limit = CHUNK_SIZE * MAX_CHUNKS_PER_RUN,
        )

        if (guestIds.isEmpty()) {
            return
        }

        var totalArchived = 0

        for (chunk in guestIds.chunked(CHUNK_SIZE)) {
            val result = archivedGuestDaoService.archiveAndDeleteGuests(chunk)
            logger.info { "iteration result: $result" }
            totalArchived += result.archivedGuests
        }

        if (totalArchived > 0) {
            logger.info { "total archived $totalArchived" }
        }
    }

    private companion object {
        // 10_000
        const val CHUNK_SIZE = 500
        const val MAX_CHUNKS_PER_RUN = 20
    }

}
