package io.elephantchess.db.services

import io.elephantchess.utils.di.KoinSingleton

import io.elephantchess.db.dao.codegen.Tables.ARCHIVED_PAGE_VIEW_DAILY
import io.elephantchess.db.dao.codegen.Tables.PAGE_VIEW_EVENT
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.utils.accumulate
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.db.utils.isBefore
import io.elephantchess.db.utils.localDate
import io.elephantchess.db.utils.ownProfileViewCondition
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Archives page views into [ARCHIVED_PAGE_VIEW_DAILY] (the same table used when archiving guests) and
 * deletes the underlying [PAGE_VIEW_EVENT] rows, so the views are no longer tied to a user.
 *
 * Views are aggregated by the day they happened and by url, counting unique viewers per day+url. The
 * "own" / "other" profile view buckets are computed here (while the viewing user still exists) because
 * "own" means the viewed profile's handle equals the viewer's handle, which can't be reconstructed once
 * the user is gone. Mirrors [ArchivedGuestDaoService]'s page-view archiving so both feed the same
 * aggregated metrics.
 */
@KoinSingleton
class ArchivedPageViewDaoService(private val dslContext: DSLContext) {

    /**
     * Selects up to [limit] ids of page view events older than [maxAge]. Fetched once per batch run so
     * the (indexed) age scan is not repeated for every chunk; see ArchiveOldPageViewsBatch.
     */
    suspend fun selectOldPageViewEventIds(maxAge: Duration, limit: Int): List<String> {
        val cutoff = Clock.System.now() - maxAge
        return dslContext
            .select(PAGE_VIEW_EVENT.EVENT_ID)
            .from(PAGE_VIEW_EVENT)
            .where(PAGE_VIEW_EVENT.EVENT_TIME.isBefore(cutoff))
            .limit(limit)
            .awaitRecords()
            .map { it.get(PAGE_VIEW_EVENT.EVENT_ID) }
    }

    /**
     * Archives and deletes the page view events with the given [eventIds]. Returns the number of
     * archived (deleted) page view events.
     */
    suspend fun archiveAndDeletePageViews(eventIds: List<String>): Int {
        if (eventIds.isEmpty()) {
            return 0
        }

        return dslContext.transactionCoroutine { cfg ->
            val transactional = DSL.using(cfg)

            archivePageViews(transactional, PAGE_VIEW_EVENT.EVENT_ID.`in`(eventIds))

            transactional
                .deleteFrom(PAGE_VIEW_EVENT)
                .where(PAGE_VIEW_EVENT.EVENT_ID.`in`(eventIds))
                .awaitExecute()
        }
    }

    /**
     * Archives and deletes all page views of the given [userId], regardless of age. Used by the "archive
     * now my views" action on the user settings page. Returns the number of archived page view events.
     */
    suspend fun archiveAndDeletePageViewsForUser(userId: String): Int {
        val eventIds = dslContext
            .select(PAGE_VIEW_EVENT.EVENT_ID)
            .from(PAGE_VIEW_EVENT)
            .where(PAGE_VIEW_EVENT.USER_ID.eq(userId))
            .awaitRecords()
            .map { it.get(PAGE_VIEW_EVENT.EVENT_ID) }

        return archiveAndDeletePageViews(eventIds)
    }

    /**
     * Number of page views currently recorded for [userId] (not yet archived).
     */
    suspend fun countPageViewsForUser(userId: String): Int {
        return dslContext
            .selectCount()
            .from(PAGE_VIEW_EVENT)
            .where(PAGE_VIEW_EVENT.USER_ID.eq(userId))
            .awaitSingleValue<Int>() ?: 0
    }

    /**
     * The [limit] most recent page views of [userId], most recent first, starting at [offset].
     */
    suspend fun listPageViewsForUser(userId: String, limit: Int, offset: Int): List<PageViewRecord> {
        return dslContext
            .select(PAGE_VIEW_EVENT.EVENT_PATH, PAGE_VIEW_EVENT.EVENT_TIME)
            .from(PAGE_VIEW_EVENT)
            .where(PAGE_VIEW_EVENT.USER_ID.eq(userId))
            .orderBy(PAGE_VIEW_EVENT.EVENT_TIME.desc())
            .limit(limit)
            .offset(offset)
            .awaitRecords()
            .map { record ->
                PageViewRecord(
                    url = record.get(PAGE_VIEW_EVENT.EVENT_PATH),
                    eventTime = record.get(PAGE_VIEW_EVENT.EVENT_TIME),
                )
            }
    }

    /**
     * Archives the [PAGE_VIEW_EVENT] rows matched by [selector] into [ARCHIVED_PAGE_VIEW_DAILY]. Shared
     * with [ArchivedGuestDaoService] (which passes a user-id selector) so both archive flows aggregate
     * page views identically. Runs on the caller-provided [transactional] context.
     */
    internal suspend fun archivePageViews(transactional: DSLContext, selector: Condition) {
        val eventDay = PAGE_VIEW_EVENT.EVENT_TIME.localDate(null)
        // truncate to the archive column width so overly long paths (long query strings) never overflow;
        // inline the bounds so the SELECT and GROUP BY expressions render identically for Postgres
        val url = DSL.substring(PAGE_VIEW_EVENT.EVENT_PATH, DSL.inline(1), DSL.inline(URL_MAX_LENGTH))
        val uniqueViewers = DSL.countDistinct(PAGE_VIEW_EVENT.USER_ID)

        val isProfileView = PAGE_VIEW_EVENT.EVENT_PATH.like("/@/%")
            .and(PAGE_VIEW_EVENT.EVENT_PATH.notLike("/@/%/%"))
        val ownProfileView = isProfileView.and(ownProfileViewCondition())
        val otherProfileView = isProfileView.and(ownProfileViewCondition().not())
        val uniqueOwnProfileViewers = DSL.countDistinct(PAGE_VIEW_EVENT.USER_ID).filterWhere(ownProfileView)
        val uniqueOtherProfileViewers = DSL.countDistinct(PAGE_VIEW_EVENT.USER_ID).filterWhere(otherProfileView)

        transactional
            .insertInto(
                ARCHIVED_PAGE_VIEW_DAILY,
                ARCHIVED_PAGE_VIEW_DAILY.DAY,
                ARCHIVED_PAGE_VIEW_DAILY.URL,
                ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS,
                ARCHIVED_PAGE_VIEW_DAILY.OWN_PROFILE_PAGE_VIEWS,
                ARCHIVED_PAGE_VIEW_DAILY.OTHER_PROFILE_PAGE_VIEWS,
            )
            .select(
                transactional
                    .select(eventDay, url, uniqueViewers, uniqueOwnProfileViewers, uniqueOtherProfileViewers)
                    .from(PAGE_VIEW_EVENT)
                    .leftJoin(USER).on(USER.ID.eq(PAGE_VIEW_EVENT.USER_ID))
                    .where(selector)
                    .groupBy(eventDay, url)
            )
            .onConflict(ARCHIVED_PAGE_VIEW_DAILY.DAY, ARCHIVED_PAGE_VIEW_DAILY.URL)
            .doUpdate()
            .accumulate(ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS)
            .accumulate(ARCHIVED_PAGE_VIEW_DAILY.OWN_PROFILE_PAGE_VIEWS)
            .accumulate(ARCHIVED_PAGE_VIEW_DAILY.OTHER_PROFILE_PAGE_VIEWS)
            .awaitExecute()
    }

    data class PageViewRecord(
        val url: String,
        val eventTime: kotlin.time.Instant,
    )

    private companion object {
        // matches the archived_page_view_daily.url column width
        const val URL_MAX_LENGTH = 2048
    }

}
