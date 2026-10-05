package io.elephantchess.servicelayer.batch

import io.elephantchess.db.services.ArchivedPageViewDaoService
import io.elephantchess.servicelayer.batch.definitions.SinglePodBatch
import io.elephantchess.servicelayer.services.analytics.ARCHIVE_PAGE_VIEW_AFTER_DAYS
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration.Companion.days

/**
 * Archives page views older than [ARCHIVE_PAGE_VIEW_AFTER_DAYS] days into the aggregated
 * `archived_page_view_daily` table and deletes the underlying rows, so they are no longer tied to a
 * user. This complements [ArchiveOldGuestsBatch], which only archives views of deleted guests: here all
 * views, including those of authenticated users, are archived once old enough.
 *
 * Views are processed in bounded chunks so a single run stays cheap; the schedule drains the backlog
 * over multiple runs.
 */
class ArchiveOldPageViewsBatch(
    private val archivedPageViewDaoService: ArchivedPageViewDaoService,
    override val logger: KLogger,
) : SinglePodBatch {

    override val podNumber: Int = 1

    override suspend fun run() {
        val eventIds = archivedPageViewDaoService.selectOldPageViewEventIds(
            maxAge = ARCHIVE_PAGE_VIEW_AFTER_DAYS.days,
            limit = CHUNK_SIZE * MAX_CHUNKS_PER_RUN,
        )

        if (eventIds.isEmpty()) {
            return
        }

        var totalArchived = 0

        for (chunk in eventIds.chunked(CHUNK_SIZE)) {
            totalArchived += archivedPageViewDaoService.archiveAndDeletePageViews(chunk)
        }

        if (totalArchived > 0) {
            logger.info { "total archived page views $totalArchived" }
        }
    }

    private companion object {
        const val CHUNK_SIZE = 10_000
        const val MAX_CHUNKS_PER_RUN = 20
    }

}
