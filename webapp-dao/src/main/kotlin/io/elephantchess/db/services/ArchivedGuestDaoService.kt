package io.elephantchess.db.services

import io.elephantchess.utils.di.KoinSingleton

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
import io.elephantchess.db.model.ArchiveResult
import io.elephantchess.db.model.analytics.DailyValueRecord
import io.elephantchess.db.model.analytics.MonthlyPageViewRecord
import io.elephantchess.db.utils.accumulate
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.currentTimestamp
import io.elephantchess.db.utils.diffInSeconds
import io.elephantchess.db.utils.isBefore
import io.elephantchess.db.utils.localDate
import io.elephantchess.db.utils.yearMonthOfDay
import io.elephantchess.model.UserType
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Select
import org.jooq.impl.DSL
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
@KoinSingleton
class ArchivedGuestDaoService(
    private val dslContext: DSLContext,
    private val archivedPageViewDaoService: ArchivedPageViewDaoService,
) {

    /**
     * Selects up to [limit] guests that are older than [maxAge] and inactive for at least [maxAge] and
     * are archivable (not referenced by any table other than the ones deleted along with them).
     *
     * This runs the expensive multi-table anti-join [archivableCondition] exactly once; callers should
     * drain the returned ids in small chunks via [archiveAndDeleteGuests] instead of re-running the scan
     * per chunk. Read-only, so it holds no write locks.
     */
    suspend fun selectArchivableGuestIds(maxAge: Duration, limit: Int): List<String> {
        val cutoff = Clock.System.now() - maxAge

        return dslContext
            .select(USER.ID)
            .from(USER)
            .where(archivableCondition(cutoff))
            .limit(limit)
            .awaitRecords()
            .map { it.get(USER.ID) }
    }

    /**
     * Archives and deletes at most [batchSize] guests that are older than [maxAge] and inactive for at
     * least [maxAge]. Runs in a single transaction so archiving and deletion are atomic.
     */
    suspend fun archiveAndDeleteOldGuests(maxAge: Duration, batchSize: Int): ArchiveResult =
        archiveAndDeleteGuests(selectArchivableGuestIds(maxAge, batchSize))

    /**
     * Archives and deletes the given [guestIds] in a single transaction so archiving and deletion are
     * atomic. The caller is responsible for selecting the ids (see [selectArchivableGuestIds]).
     */
    suspend fun archiveAndDeleteGuests(guestIds: List<String>): ArchiveResult {
        if (guestIds.isEmpty()) {
            return ArchiveResult(0, 0, 0, 0)
        }

        return dslContext.transactionCoroutine { cfg ->
            val transactional = DSL.using(cfg)

            archiveGuestCounts(transactional, guestIds)
            archivedPageViewDaoService.archivePageViews(transactional, PAGE_VIEW_EVENT.USER_ID.`in`(guestIds))
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

            ArchiveResult(
                archivedGuests = guestIds.size,
                deletedPageViews = deletedPageViews,
                deletedSearchQueries = deletedSearchQueries,
                deletedSessions = deletedSessions,
            )
        }
    }

    /**
     * Total number of archived guests whose session lasted at least 30 minutes (the [ARCHIVED_GUEST_DAILY.GUESTS_OTHER]
     * bucket). Mirrors the live `countGuestsWithSessionAtLeast(30.minutes)` count used on the "Global" page so
     * archived guests (deleted from the `user` table) are still counted in total users.
     */
    suspend fun countArchivedGuestsWithSessionAtLeast30Min(): Int {
        val total = DSL.coalesce(DSL.sum(ARCHIVED_GUEST_DAILY.GUESTS_OTHER), DSL.inline(0))

        return dslContext
            .select(total)
            .from(ARCHIVED_GUEST_DAILY)
            .awaitRecords()
            .firstOrNull()
            ?.get(total)
            ?.toInt()
            ?: 0
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

    /**
     * Archived monthly "own profile" page views (guests viewing their own `/@/{handle}` profile),
     * summed per month. Mirrors [io.elephantchess.db.services.PageViewEventDaoService.fetchMonthlyOwnUserProfilePageViews]
     * so archived rows can be summed into the live series; label matches the live `/@/{username}` label.
     */
    suspend fun fetchArchivedMonthlyOwnProfilePageViews(): List<MonthlyPageViewRecord> =
        fetchArchivedMonthlyProfilePageViews(ARCHIVED_PAGE_VIEW_DAILY.OWN_PROFILE_PAGE_VIEWS)

    /**
     * Archived monthly "other profile" page views (guests viewing another user's `/@/{handle}` profile),
     * summed per month. Mirrors [io.elephantchess.db.services.PageViewEventDaoService.fetchMonthlyOtherUserProfilePageViews].
     */
    suspend fun fetchArchivedMonthlyOtherProfilePageViews(): List<MonthlyPageViewRecord> =
        fetchArchivedMonthlyProfilePageViews(ARCHIVED_PAGE_VIEW_DAILY.OTHER_PROFILE_PAGE_VIEWS)

    private suspend fun fetchArchivedMonthlyProfilePageViews(
        column: org.jooq.TableField<*, Int>,
    ): List<MonthlyPageViewRecord> {
        val month = ARCHIVED_PAGE_VIEW_DAILY.DAY.yearMonthOfDay()
        val pageViews = DSL.sum(column).`as`("page_views")

        return dslContext
            .select(month, pageViews)
            .from(ARCHIVED_PAGE_VIEW_DAILY)
            .groupBy(month)
            .having(DSL.sum(column).gt(DSL.inline(java.math.BigDecimal.ZERO)))
            .awaitRecords()
            .map { record ->
                MonthlyPageViewRecord(
                    yearMonth = record.get(month),
                    label = "/@/{username}",
                    uniquePageViews = record.get(pageViews).toInt(),
                )
            }
    }

    private suspend fun archiveGuestCounts(transactional: DSLContext, guestIds: List<String>) {
        val creationDay = USER.CREATION.localDate(null)
        val lifespan = diffInSeconds(USER.LAST_ONLINE, USER.CREATION)

        fun bucket(condition: Condition) =
            DSL.count().filterWhere(condition)

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
                    .select(
                        creationDay,
                        under1min,
                        under5min,
                        under15min,
                        under30min,
                        other
                    )
                    .from(USER)
                    .where(USER.ID.`in`(guestIds))
                    .groupBy(creationDay)
            )
            .onConflict(ARCHIVED_GUEST_DAILY.DAY)
            .doUpdate()
            .accumulate(ARCHIVED_GUEST_DAILY.GUESTS_UNDER_1MIN)
            .accumulate(ARCHIVED_GUEST_DAILY.GUESTS_UNDER_5MIN)
            .accumulate(ARCHIVED_GUEST_DAILY.GUESTS_UNDER_15MIN)
            .accumulate(ARCHIVED_GUEST_DAILY.GUESTS_UNDER_30MIN)
            .accumulate(ARCHIVED_GUEST_DAILY.GUESTS_OTHER)
            .set(ARCHIVED_GUEST_DAILY.UPDATED_AT, currentTimestamp(ARCHIVED_GUEST_DAILY.UPDATED_AT))
            .awaitExecute()
    }

    /**
     * Archives the guests' database search queries into [ARCHIVED_GUEST_DAILY], counted by the day the
     * query happened (matching the live "db searches" metric). Reuses the [ARCHIVED_GUEST_DAILY.DAY]
     * primary key: guest-count columns default to 0 for days that only carry searches, and vice versa.
     */
    private suspend fun archiveSearchQueries(transactional: DSLContext, guestIds: List<String>) {
        val queryDay = REFERENCE_GAME_SEARCH_QUERY.QUERY_TIME.localDate(null)
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
            .accumulate(ARCHIVED_GUEST_DAILY.DELETED_SEARCHES)
            .set(ARCHIVED_GUEST_DAILY.UPDATED_AT, currentTimestamp(ARCHIVED_GUEST_DAILY.UPDATED_AT))
            .awaitExecute()
    }

    /**
     * A guest is archivable when it is a guest, older than [cutoff], inactive since [cutoff] and not
     * referenced by any table other than the ones deleted together with it (page views, database search
     * queries and sessions).
     */
    private fun archivableCondition(cutoff: Instant): Condition {
        return USER.USER_TYPE.eq(UserType.GUEST)
            .and(USER.LAST_ONLINE.isBefore(cutoff))
            .andNotExists(gameExists())
            .andNotExists(botGameExists())
            .andNotExists(puzzleResultExists())
            .andNotExists(analysisExists())
            .andNotExists(gameStatusEventExists())
            .andNotExists(gameChatMessageExists())
            .andNotExists(discordGameNotificationExists())
            // no seven kingdoms games exist yet; skip these anti-joins (and their indexes) until they do.
            // .andNotExists(sevenKingdomsGameExists())
            // .andNotExists(sevenKingdomsGameEventExists())
            .andNotExists(referencePlayerProfileEditExists())
            .andNotExists(referencePlayerProfileEditSourceExists())
            .andNotExists(kofiEventExists())
            .andNotExists(upcomingEventExists())
    }

    private fun gameExists(): Select<*> =
        DSL
            .selectOne()
            .from(GAME)
            .where(
                GAME.INVITER.eq(USER.ID)
                    .or(GAME.INVITEE.eq(USER.ID))
                    .or(GAME.DRAW_PROPOSITION_USER.eq(USER.ID))
            )

    private fun botGameExists(): Select<*> =
        DSL
            .selectOne()
            .from(BOT_GAME)
            .where(BOT_GAME.USER_ID.eq(USER.ID))

    private fun puzzleResultExists(): Select<*> =
        DSL
            .selectOne()
            .from(PUZZLE_RESULT)
            .where(PUZZLE_RESULT.USER_ID.eq(USER.ID))

    private fun analysisExists(): Select<*> =
        DSL
            .selectOne()
            .from(ANALYSIS)
            .where(ANALYSIS.OWNER_USER_ID.eq(USER.ID))

    private fun gameStatusEventExists(): Select<*> =
        DSL
            .selectOne()
            .from(GAME_STATUS_EVENT)
            .where(GAME_STATUS_EVENT.USER_ID.eq(USER.ID))

    private fun gameChatMessageExists(): Select<*> =
        DSL
            .selectOne()
            .from(GAME_CHAT_MESSAGE)
            .where(GAME_CHAT_MESSAGE.AUTHOR.eq(USER.ID))

    private fun discordGameNotificationExists(): Select<*> =
        DSL
            .selectOne()
            .from(DISCORD_GAME_NOTIFICATION)
            .where(DISCORD_GAME_NOTIFICATION.USER_ID.eq(USER.ID))

    private fun sevenKingdomsGameExists(): Select<*> =
        DSL
            .selectOne()
            .from(SEVEN_KINGDOMS_GAME)
            .where(
                SEVEN_KINGDOMS_GAME.PLAYER_WHITE.eq(USER.ID)
                    .or(SEVEN_KINGDOMS_GAME.PLAYER_RED.eq(USER.ID))
                    .or(SEVEN_KINGDOMS_GAME.PLAYER_ORANGE.eq(USER.ID))
                    .or(SEVEN_KINGDOMS_GAME.PLAYER_BLUE.eq(USER.ID))
                    .or(SEVEN_KINGDOMS_GAME.PLAYER_GREEN.eq(USER.ID))
                    .or(SEVEN_KINGDOMS_GAME.PLAYER_PURPLE.eq(USER.ID))
                    .or(SEVEN_KINGDOMS_GAME.PLAYER_BLACK.eq(USER.ID))
            )

    private fun sevenKingdomsGameEventExists(): Select<*> =
        DSL
            .selectOne()
            .from(SEVEN_KINGDOMS_GAME_EVENT)
            .where(SEVEN_KINGDOMS_GAME_EVENT.USER_ID.eq(USER.ID))

    private fun referencePlayerProfileEditExists(): Select<*> =
        DSL
            .selectOne()
            .from(REFERENCE_PLAYER_PROFILE_EDIT)
            .where(REFERENCE_PLAYER_PROFILE_EDIT.EDITOR_ID.eq(USER.ID))

    private fun referencePlayerProfileEditSourceExists(): Select<*> =
        DSL
            .selectOne()
            .from(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE)
            .where(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.EDITOR_ID.eq(USER.ID))

    private fun kofiEventExists(): Select<*> =
        DSL
            .selectOne()
            .from(KOFI_EVENT)
            .where(KOFI_EVENT.MATCHED_USER_ID.eq(USER.ID))

    private fun upcomingEventExists(): Select<*> =
        DSL
            .selectOne()
            .from(UPCOMING_EVENT)
            .where(UPCOMING_EVENT.CREATED_BY.eq(USER.ID))

    private companion object {
        const val LIFESPAN_1_MIN = 60
        const val LIFESPAN_5_MIN = 5 * 60
        const val LIFESPAN_15_MIN = 15 * 60
        const val LIFESPAN_30_MIN = 30 * 60
    }

}
