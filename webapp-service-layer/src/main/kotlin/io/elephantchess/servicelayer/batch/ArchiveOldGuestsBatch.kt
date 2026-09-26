package io.elephantchess.servicelayer.batch

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
class ArchiveOldGuestsBatch(
    private val archivedGuestDaoService: ArchivedGuestDaoService,
    override val logger: KLogger,
) : SinglePodBatch {

    override val podNumber: Int = 0

    override suspend fun run() {
        var totalArchived = 0
        var iterations = 0

        while (iterations < MAX_CHUNKS_PER_RUN) {
            val result = archivedGuestDaoService.archiveAndDeleteOldGuests(
                maxAge = ARCHIVE_GUEST_AFTER_DAYS.days,
                batchSize = CHUNK_SIZE,
            )

            totalArchived += result.archivedGuests
            iterations++

            if (result.archivedGuests < CHUNK_SIZE) {
                break
            }
        }

        if (totalArchived > 0) {
            logger.info { "Archived and deleted $totalArchived old guest users" }
        }
    }

    private companion object {
        const val CHUNK_SIZE = 1_000
        const val MAX_CHUNKS_PER_RUN = 20
    }

}
