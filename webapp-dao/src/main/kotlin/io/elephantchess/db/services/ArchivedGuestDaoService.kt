package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.ANALYSIS
import io.elephantchess.db.dao.codegen.Tables.ARCHIVED_GUEST_DAILY
import io.elephantchess.db.dao.codegen.Tables.ARCHIVED_PAGE_VIEW_DAILY
import io.elephantchess.db.dao.codegen.Tables.BOT_GAME
import io.elephantchess.db.dao.codegen.Tables.DISCORD_GAME_NOTIFICATION
import io.elephantchess.db.dao.codegen.Tables.GAME
import io.elephantchess.db.dao.codegen.Tables.GAME_CHAT_MESSAGE
import io.elephantchess.db.dao.codegen.Tables.GAME_STATUS_EVENT
import io.elephantchess.db.dao.codegen.Tables.KOFI_EVENT
import io.elephantchess.db.dao.codegen.Tables.PAGE_VIEW_EVENT
import io.elephantchess.db.dao.codegen.Tables.PUZZLE_RESULT
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME_SEARCH_QUERY
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_PLAYER_PROFILE_EDIT
import io.elephantchess.db.dao.codegen.Tables.REFERENCE_PLAYER_PROFILE_EDIT_SOURCE
import io.elephantchess.db.dao.codegen.Tables.SEVEN_KINGDOMS_GAME
import io.elephantchess.db.dao.codegen.Tables.SEVEN_KINGDOMS_GAME_EVENT
import io.elephantchess.db.dao.codegen.Tables.UPCOMING_EVENT
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.dao.codegen.Tables.USER_SESSION
import io.elephantchess.db.model.analytics.DailyValueRecord
import io.elephantchess.db.model.analytics.MonthlyPageViewRecord
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.diffInSeconds
import io.elephantchess.db.utils.yearMonthOfDay
import io.elephantchess.model.UserType
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.time.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Archives inactive guest users into aggregated daily tables and deletes the underlying rows.
 *
 * A guest is archivable when it is older than a given age, has no activity for that same period and is
 * not referenced by any table other than the ones that are deleted along with it (page views, database
 * search queries and sessions). This keeps the deletion free of foreign-key violations.
 *
 * Guests are archived by their creation day, bucketed by lifespan (creation to last activity), while
 * their page views and database search queries are archived by the day they happened.
 */
class ArchivedGuestDaoService(private val dslContext: DSLContext) {

    data class ArchiveResult(
        val archivedGuests: Int,
        val deletedPageViews: Int,
        val deletedSearchQueries: Int,
        val deletedSessions: Int,
    ) {
        companion object {
            val EMPTY = ArchiveResult(0, 0, 0, 0)
        }
    }

    /**
     * Archives and deletes at most [batchSize] guests that are older than [maxAge] and inactive for at
     * least [maxAge]. Runs in a single transaction so archiving and deletion are atomic.
     */
    suspend fun archiveAndDeleteOldGuests(maxAge: Duration, batchSize: Int): ArchiveResult {
        val cutoff = Clock.System.now() - maxAge

        var result = ArchiveResult.EMPTY

        dslContext.transactionCoroutine { cfg ->
            val transactional = DSL.using(cfg)

            val guestIds = transactional
                .select(USER.ID)
                .from(USER)
                .where(archivableCondition(cutoff))
                .limit(batchSize)
                .awaitRecords()
                .map { it.get(USER.ID) }

            if (guestIds.isEmpty()) {
                return@transactionCoroutine
            }

            archiveGuestCounts(transactional, guestIds)
            archivePageViews(transactional, guestIds)
            archiveSearchQueries(transactional, guestIds)

            val deletedPageViews = transactional
                .deleteFrom(PAGE_VIEW_EVENT)
                .where(PAGE_VIEW_EVENT.USER_ID.`in`(guestIds))
                .awaitExecute()

            val deletedSearchQueries = transactional
                .deleteFrom(REFERENCE_GAME_SEARCH_QUERY)
                .where(REFERENCE_GAME_SEARCH_QUERY.USER_ID.`in`(guestIds))
                .awaitExecute()

            val deletedSessions = transactional
                .deleteFrom(USER_SESSION)
                .where(USER_SESSION.USER_ID.`in`(guestIds))
                .awaitExecute()

            transactional
                .deleteFrom(USER)
                .where(USER.ID.`in`(guestIds))
                .awaitExecute()

            result = ArchiveResult(
                archivedGuests = guestIds.size,
                deletedPageViews = deletedPageViews,
                deletedSearchQueries = deletedSearchQueries,
                deletedSessions = deletedSessions,
            )
        }

        return result
    }

    /**
     * Archived unique daily page views (one per guest per day per url), aggregated by day, over the last
     * [days]. Mirrors the counting used for live page views so both can be summed.
     */
    suspend fun fetchArchivedPageViewsByDay(days: Int): List<DailyValueRecord> {
        val pageViews = DSL.sum(ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS).`as`("page_views")

        return dslContext
            .select(ARCHIVED_PAGE_VIEW_DAILY.DAY, pageViews)
            .from(ARCHIVED_PAGE_VIEW_DAILY)
            .where(ARCHIVED_PAGE_VIEW_DAILY.DAY.ge(LocalDate.now().minusDays(days.toLong())))
            .groupBy(ARCHIVED_PAGE_VIEW_DAILY.DAY)
            .orderBy(ARCHIVED_PAGE_VIEW_DAILY.DAY.asc())
            .awaitRecords()
            .map { record ->
                DailyValueRecord(
                    day = record.get(ARCHIVED_PAGE_VIEW_DAILY.DAY),
                    value = record.get(pageViews).toInt(),
                )
            }
    }

    /**
     * Archived monthly page views for the given [eventPath] (and its tracked query-parameter variants),
     * bucketed by month and url. Mirrors [io.elephantchess.db.services.PageViewEventDaoService.fetchMonthlyPageViews]
     * so archived rows can be summed into the live per-url monthly page views.
     */
    suspend fun fetchArchivedMonthlyPageViews(eventPath: String): List<MonthlyPageViewRecord> {
        val month = ARCHIVED_PAGE_VIEW_DAILY.DAY.yearMonthOfDay()
        val pageViews = DSL.sum(ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS).`as`("page_views")

        return dslContext
            .select(month, ARCHIVED_PAGE_VIEW_DAILY.URL, pageViews)
            .from(ARCHIVED_PAGE_VIEW_DAILY)
            .where(
                ARCHIVED_PAGE_VIEW_DAILY.URL.eq(eventPath)
                    .or(ARCHIVED_PAGE_VIEW_DAILY.URL.like("$eventPath?medium=%"))
                    .or(ARCHIVED_PAGE_VIEW_DAILY.URL.like("$eventPath?gad_source=1%"))
                    .or(ARCHIVED_PAGE_VIEW_DAILY.URL.like("$eventPath?fbclid=%"))
            )
            .groupBy(month, ARCHIVED_PAGE_VIEW_DAILY.URL)
            .awaitRecords()
            .map { record ->
                MonthlyPageViewRecord(
                    yearMonth = record.get(month),
                    label = record.get(ARCHIVED_PAGE_VIEW_DAILY.URL),
                    uniquePageViews = record.get(pageViews).toInt(),
                )
            }
    }

    private suspend fun archiveGuestCounts(transactional: DSLContext, guestIds: List<String>) {
        val creationDay = dayExpr(USER.CREATION)
        val lifespan = diffInSeconds(USER.LAST_ONLINE, USER.CREATION)

        fun bucket(condition: Condition) = DSL.count().filterWhere(condition)

        val under1min = bucket(lifespan.lt(LIFESPAN_1_MIN))
        val under5min = bucket(lifespan.ge(LIFESPAN_1_MIN).and(lifespan.lt(LIFESPAN_5_MIN)))
        val under15min = bucket(lifespan.ge(LIFESPAN_5_MIN).and(lifespan.lt(LIFESPAN_15_MIN)))
        val under30min = bucket(lifespan.ge(LIFESPAN_15_MIN).and(lifespan.lt(LIFESPAN_30_MIN)))
        val other = bucket(lifespan.ge(LIFESPAN_30_MIN))

        transactional
            .insertInto(
                ARCHIVED_GUEST_DAILY,
                ARCHIVED_GUEST_DAILY.DAY,
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_1MIN,
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_5MIN,
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_15MIN,
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_30MIN,
                ARCHIVED_GUEST_DAILY.GUESTS_OTHER,
            )
            .select(
                transactional
                    .select(creationDay, under1min, under5min, under15min, under30min, other)
                    .from(USER)
                    .where(USER.ID.`in`(guestIds))
                    .groupBy(creationDay)
            )
            .onConflict(ARCHIVED_GUEST_DAILY.DAY)
            .doUpdate()
            .set(
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_1MIN,
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_1MIN.plus(DSL.excluded(ARCHIVED_GUEST_DAILY.GUESTS_UNDER_1MIN)),
            )
            .set(
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_5MIN,
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_5MIN.plus(DSL.excluded(ARCHIVED_GUEST_DAILY.GUESTS_UNDER_5MIN)),
            )
            .set(
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_15MIN,
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_15MIN.plus(DSL.excluded(ARCHIVED_GUEST_DAILY.GUESTS_UNDER_15MIN)),
            )
            .set(
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_30MIN,
                ARCHIVED_GUEST_DAILY.GUESTS_UNDER_30MIN.plus(DSL.excluded(ARCHIVED_GUEST_DAILY.GUESTS_UNDER_30MIN)),
            )
            .set(
                ARCHIVED_GUEST_DAILY.GUESTS_OTHER,
                ARCHIVED_GUEST_DAILY.GUESTS_OTHER.plus(DSL.excluded(ARCHIVED_GUEST_DAILY.GUESTS_OTHER)),
            )
            .awaitExecute()
    }

    private suspend fun archivePageViews(transactional: DSLContext, guestIds: List<String>) {
        val eventDay = dayExpr(PAGE_VIEW_EVENT.EVENT_TIME)
        // truncate to the archive column width so overly long paths (long query strings) never overflow;
        // inline the bounds so the SELECT and GROUP BY expressions render identically for Postgres
        val url = DSL.substring(PAGE_VIEW_EVENT.EVENT_PATH, DSL.inline(1), DSL.inline(URL_MAX_LENGTH))
        val uniqueGuests = DSL.countDistinct(PAGE_VIEW_EVENT.USER_ID)

        transactional
            .insertInto(
                ARCHIVED_PAGE_VIEW_DAILY,
                ARCHIVED_PAGE_VIEW_DAILY.DAY,
                ARCHIVED_PAGE_VIEW_DAILY.URL,
                ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS,
            )
            .select(
                transactional
                    .select(eventDay, url, uniqueGuests)
                    .from(PAGE_VIEW_EVENT)
                    .where(PAGE_VIEW_EVENT.USER_ID.`in`(guestIds))
                    .groupBy(eventDay, url)
            )
            .onConflict(ARCHIVED_PAGE_VIEW_DAILY.DAY, ARCHIVED_PAGE_VIEW_DAILY.URL)
            .doUpdate()
            .set(
                ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS,
                ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS.plus(DSL.excluded(ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS)),
            )
            .awaitExecute()
    }

    /**
     * Archives the guests' database search queries into [ARCHIVED_GUEST_DAILY], counted by the day the
     * query happened (matching the live "db searches" metric). Reuses the [ARCHIVED_GUEST_DAILY.DAY]
     * primary key: guest-count columns default to 0 for days that only carry searches, and vice versa.
     */
    private suspend fun archiveSearchQueries(transactional: DSLContext, guestIds: List<String>) {
        val queryDay = dayExpr(REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME)
        val searchCount = DSL.count()

        transactional
            .insertInto(
                ARCHIVED_GUEST_DAILY,
                ARCHIVED_GUEST_DAILY.DAY,
                ARCHIVED_GUEST_DAILY.DELETED_SEARCHES,
            )
            .select(
                transactional
                    .select(queryDay, searchCount)
                    .from(REFERENCE_GAME_SEARCH_QUERY)
                    .where(REFERENCE_GAME_SEARCH_QUERY.USER_ID.`in`(guestIds))
                    .groupBy(queryDay)
            )
            .onConflict(ARCHIVED_GUEST_DAILY.DAY)
            .doUpdate()
            .set(
                ARCHIVED_GUEST_DAILY.DELETED_SEARCHES,
                ARCHIVED_GUEST_DAILY.DELETED_SEARCHES.plus(DSL.excluded(ARCHIVED_GUEST_DAILY.DELETED_SEARCHES)),
            )
            .awaitExecute()
    }

    /**
     * A guest is archivable when it is a guest, older than [cutoff], inactive since [cutoff] and not
     * referenced by any table other than the ones deleted together with it (page views, database search
     * queries and sessions).
     */
    private fun archivableCondition(cutoff: Instant): Condition {
        val lastActivity = DSL.coalesce(USER.LAST_ONLINE, USER.CREATION)

        return USER.USER_TYPE.eq(UserType.GUEST)
            .and(USER.CREATION.lessThan(cutoff))
            .and(lastActivity.lessThan(cutoff))
            .andNotExists(
                DSL
                    .selectOne()
                    .from(GAME)
                    .where(
                        GAME.INVITER.eq(USER.ID)
                            .or(GAME.INVITEE.eq(USER.ID))
                            .or(GAME.DRAW_PROPOSITION_USER.eq(USER.ID))
                    )
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(BOT_GAME)
                    .where(BOT_GAME.USER_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(PUZZLE_RESULT)
                    .where(PUZZLE_RESULT.USER_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(ANALYSIS)
                    .where(ANALYSIS.OWNER_USER_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(GAME_STATUS_EVENT)
                    .where(GAME_STATUS_EVENT.USER_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(GAME_CHAT_MESSAGE)
                    .where(GAME_CHAT_MESSAGE.AUTHOR.eq(USER.ID))
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(DISCORD_GAME_NOTIFICATION)
                    .where(DISCORD_GAME_NOTIFICATION.USER_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL.selectOne().from(SEVEN_KINGDOMS_GAME).where(
                    SEVEN_KINGDOMS_GAME.PLAYER_WHITE.eq(USER.ID)
                        .or(SEVEN_KINGDOMS_GAME.PLAYER_RED.eq(USER.ID))
                        .or(SEVEN_KINGDOMS_GAME.PLAYER_ORANGE.eq(USER.ID))
                        .or(SEVEN_KINGDOMS_GAME.PLAYER_BLUE.eq(USER.ID))
                        .or(SEVEN_KINGDOMS_GAME.PLAYER_GREEN.eq(USER.ID))
                        .or(SEVEN_KINGDOMS_GAME.PLAYER_PURPLE.eq(USER.ID))
                        .or(SEVEN_KINGDOMS_GAME.PLAYER_BLACK.eq(USER.ID))
                )
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(SEVEN_KINGDOMS_GAME_EVENT)
                    .where(SEVEN_KINGDOMS_GAME_EVENT.USER_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(REFERENCE_PLAYER_PROFILE_EDIT)
                    .where(REFERENCE_PLAYER_PROFILE_EDIT.EDITOR_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE)
                    .where(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.EDITOR_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(KOFI_EVENT)
                    .where(KOFI_EVENT.MATCHED_USER_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL
                    .selectOne()
                    .from(UPCOMING_EVENT)
                    .where(UPCOMING_EVENT.CREATED_BY.eq(USER.ID))
            )
    }

    private companion object {
        const val LIFESPAN_1_MIN = 60
        const val LIFESPAN_5_MIN = 5 * 60
        const val LIFESPAN_15_MIN = 15 * 60
        const val LIFESPAN_30_MIN = 30 * 60

        // matches the archived_page_view_daily.url column width
        const val URL_MAX_LENGTH = 2048

        /**
         * The UTC calendar day of an instant, as a genuine `date` SQL expression (so it can be inserted
         * into a `date` column), while matching the `to_char` day bucketing used by the live metrics.
         */
        fun dayExpr(field: Field<Instant>): Field<LocalDate> =
            DSL.field("cast(to_char({0}, 'YYYY-MM-DD') as date)", SQLDataType.LOCALDATE, field)
    }

}
