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
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.diffInSeconds
import io.elephantchess.db.utils.localDate
import io.elephantchess.model.UserType
import org.jooq.Condition
import org.jooq.DSLContext
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
 * their page views are archived by the day the view happened.
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
     * Archived unique daily page views (one per guest per day), aggregated by day, over the last [days].
     * Mirrors the counting used for live page views so both can be summed.
     */
    suspend fun fetchArchivedPageViewsByDay(days: Int): List<DailyValueRecord> {
        return dslContext
            .select(ARCHIVED_PAGE_VIEW_DAILY.DAY, ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS)
            .from(ARCHIVED_PAGE_VIEW_DAILY)
            .where(ARCHIVED_PAGE_VIEW_DAILY.DAY.ge(LocalDate.now().minusDays(days.toLong())))
            .orderBy(ARCHIVED_PAGE_VIEW_DAILY.DAY.asc())
            .awaitRecords()
            .map { record ->
                DailyValueRecord(
                    day = record.get(ARCHIVED_PAGE_VIEW_DAILY.DAY),
                    value = record.get(ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS),
                )
            }
    }

    private suspend fun archiveGuestCounts(transactional: DSLContext, guestIds: List<String>) {
        val creationDay = USER.CREATION.localDate(null)
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
        val eventDay = PAGE_VIEW_EVENT.EVENT_TIME.localDate(null)
        val uniqueGuests = DSL.countDistinct(PAGE_VIEW_EVENT.USER_ID)

        transactional
            .insertInto(
                ARCHIVED_PAGE_VIEW_DAILY,
                ARCHIVED_PAGE_VIEW_DAILY.DAY,
                ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS,
            )
            .select(
                transactional
                    .select(eventDay, uniqueGuests)
                    .from(PAGE_VIEW_EVENT)
                    .where(PAGE_VIEW_EVENT.USER_ID.`in`(guestIds))
                    .groupBy(eventDay)
            )
            .onConflict(ARCHIVED_PAGE_VIEW_DAILY.DAY)
            .doUpdate()
            .set(
                ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS,
                ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS.plus(DSL.excluded(ARCHIVED_PAGE_VIEW_DAILY.PAGE_VIEWS)),
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
                DSL.selectOne().from(GAME).where(
                    GAME.INVITER.eq(USER.ID)
                        .or(GAME.INVITEE.eq(USER.ID))
                        .or(GAME.DRAW_PROPOSITION_USER.eq(USER.ID))
                )
            )
            .andNotExists(DSL.selectOne().from(BOT_GAME).where(BOT_GAME.USER_ID.eq(USER.ID)))
            .andNotExists(DSL.selectOne().from(PUZZLE_RESULT).where(PUZZLE_RESULT.USER_ID.eq(USER.ID)))
            .andNotExists(DSL.selectOne().from(ANALYSIS).where(ANALYSIS.OWNER_USER_ID.eq(USER.ID)))
            .andNotExists(DSL.selectOne().from(GAME_STATUS_EVENT).where(GAME_STATUS_EVENT.USER_ID.eq(USER.ID)))
            .andNotExists(DSL.selectOne().from(GAME_CHAT_MESSAGE).where(GAME_CHAT_MESSAGE.AUTHOR.eq(USER.ID)))
            .andNotExists(
                DSL.selectOne().from(DISCORD_GAME_NOTIFICATION)
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
                DSL.selectOne().from(SEVEN_KINGDOMS_GAME_EVENT)
                    .where(SEVEN_KINGDOMS_GAME_EVENT.USER_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL.selectOne().from(REFERENCE_PLAYER_PROFILE_EDIT)
                    .where(REFERENCE_PLAYER_PROFILE_EDIT.EDITOR_ID.eq(USER.ID))
            )
            .andNotExists(
                DSL.selectOne().from(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE)
                    .where(REFERENCE_PLAYER_PROFILE_EDIT_SOURCE.EDITOR_ID.eq(USER.ID))
            )
            .andNotExists(DSL.selectOne().from(KOFI_EVENT).where(KOFI_EVENT.MATCHED_USER_ID.eq(USER.ID)))
            .andNotExists(DSL.selectOne().from(UPCOMING_EVENT).where(UPCOMING_EVENT.CREATED_BY.eq(USER.ID)))
    }

    private companion object {
        const val LIFESPAN_1_MIN = 60
        const val LIFESPAN_5_MIN = 5 * 60
        const val LIFESPAN_15_MIN = 15 * 60
        const val LIFESPAN_30_MIN = 30 * 60
    }

}
