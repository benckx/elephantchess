package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.PageViewEventDao
import io.elephantchess.db.dao.codegen.tables.pojos.PageViewEvent
import io.elephantchess.db.model.analytics.DailyValueRecord
import io.elephantchess.db.model.analytics.HourlyPageViewRecord
import io.elephantchess.db.model.analytics.MonthlyPageViewRecord
import io.elephantchess.db.utils.*
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record2
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class PageViewEventDaoService(private val dslContext: DSLContext) {

    suspend fun save(record: PageViewEvent) {
        dslContext.transactionCoroutine { cfg ->
            PageViewEventDao(cfg).insertReactive(record)
        }
    }

    suspend fun fetchMonthlyPageViewsByPattern(
        pattern: String,
        excludedUserIds: List<String>
    ): List<MonthlyPageViewRecord> {
        val yearMonth = PAGE_VIEW_EVENT.EVENT_TIME.yearMonth("year_month")

        suspend fun fetchRecords(condition: Condition): List<Record2<YearMonth, Int>> {
            return dslContext
                .select(
                    yearMonth,
                    uniqueDailyPageViewsField()
                )
                .from(PAGE_VIEW_EVENT)
                .where(condition)
                .and(PAGE_VIEW_EVENT.EVENT_TIME.greaterOrEqual(startDate))
                .and(excludedUsersCondition(excludedUserIds))
                .groupBy(yearMonth)
                .orderBy(yearMonth.desc())
                .awaitRecords()
        }

        fun mapRecord(record2: Record2<YearMonth, Int>, eventPath: String): MonthlyPageViewRecord {
            return MonthlyPageViewRecord(
                yearMonth = record2.getValue(yearMonth),
                label = eventPath,
                uniquePageViews = record2.getValue("unique_page_views", Int::class.java)
            )
        }

        val condition1 = PAGE_VIEW_EVENT.EVENT_PATH.like(pattern)
        val condition2 = PAGE_VIEW_EVENT.EVENT_PATH.notLike(pattern)

        val records1 = fetchRecords(condition1)
            .map { record2 -> mapRecord(record2, pattern) }

        val records2 = fetchRecords(condition2)
            .map { record2 -> mapRecord(record2, "organic") }

        return records1 + records2
    }

    suspend fun fetchMonthlyPageViews(eventPath: String, excludedUserIds: List<String>): List<MonthlyPageViewRecord> {
        val yearMonth = PAGE_VIEW_EVENT.EVENT_TIME.yearMonth("year_month")

        return dslContext
            .select(
                yearMonth,
                PAGE_VIEW_EVENT.EVENT_PATH,
                uniqueDailyPageViewsField()
            )
            .from(PAGE_VIEW_EVENT)
            .where(
                PAGE_VIEW_EVENT.EVENT_PATH.eq(eventPath)
                    .or(PAGE_VIEW_EVENT.EVENT_PATH.like("$eventPath?medium=%"))
                    .or(PAGE_VIEW_EVENT.EVENT_PATH.like("$eventPath?gad_source=1%"))
                    .or(PAGE_VIEW_EVENT.EVENT_PATH.like("$eventPath?fbclid=%"))
            )
            .and(PAGE_VIEW_EVENT.EVENT_TIME.greaterOrEqual(startDate))
            .and(excludedUsersCondition(excludedUserIds))
            .groupBy(yearMonth, PAGE_VIEW_EVENT.EVENT_PATH)
            .orderBy(yearMonth.desc(), PAGE_VIEW_EVENT.EVENT_PATH)
            .awaitRecords()
            .map { record ->
                val yearMonthStr = record.getValue("year_month", String::class.java)
                val yearMonth = YearMonth.parse(yearMonthStr)
                MonthlyPageViewRecord(
                    yearMonth = yearMonth,
                    label = record.getValue(PAGE_VIEW_EVENT.EVENT_PATH),
                    uniquePageViews = record.getValue("unique_page_views", Int::class.java)
                )
            }
    }

    suspend fun fetchMonthlyUserProfilePageViews(excludedUserIds: List<String>): List<MonthlyPageViewRecord> {
        return fetchMonthlyUserProfilePageViewsByCondition(excludedUserIds) { DSL.noCondition() }
    }

    suspend fun fetchMonthlyOwnUserProfilePageViews(excludedUserIds: List<String>): List<MonthlyPageViewRecord> {
        return fetchMonthlyUserProfilePageViewsByCondition(excludedUserIds) { ownProfileViewCondition() }
    }

    suspend fun fetchMonthlyOtherUserProfilePageViews(excludedUserIds: List<String>): List<MonthlyPageViewRecord> {
        return fetchMonthlyUserProfilePageViewsByCondition(excludedUserIds) {
            USER.HANDLE.isNull.or(ownProfileViewCondition().not())
        }
    }

    private suspend fun fetchMonthlyUserProfilePageViewsByCondition(
        excludedUserIds: List<String>,
        additionalCondition: () -> Condition
    ): List<MonthlyPageViewRecord> {
        val yearMonth = PAGE_VIEW_EVENT.EVENT_TIME.yearMonth("year_month")

        return dslContext
            .select(
                yearMonth,
                uniqueDailyPageViewsField()
            )
            .from(PAGE_VIEW_EVENT)
            .leftJoin(USER).on(USER.ID.eq(PAGE_VIEW_EVENT.USER_ID))
            .where(PAGE_VIEW_EVENT.EVENT_PATH.like("/@/%"))
            .and(PAGE_VIEW_EVENT.EVENT_PATH.notLike("/@/%/%"))
            .and(PAGE_VIEW_EVENT.EVENT_TIME.greaterOrEqual(startDate))
            .and(excludedUsersCondition(excludedUserIds))
            .and(additionalCondition())
            .groupBy(yearMonth)
            .orderBy(yearMonth.desc())
            .awaitRecords()
            .map { record ->
                val yearMonthStr = record.getValue("year_month", String::class.java)
                val yearMonth = YearMonth.parse(yearMonthStr)
                MonthlyPageViewRecord(
                    yearMonth = yearMonth,
                    label = "/@/{username}",
                    uniquePageViews = record.getValue("unique_page_views", Int::class.java)
                )
            }
    }

    private fun ownProfileViewCondition(): Condition {
        val ownPath = DSL.concat(DSL.inline("/@/"), USER.HANDLE)
        val ownPathWithQueryParam = DSL.concat(DSL.inline("/@/"), USER.HANDLE, DSL.inline("?%"))
        return USER.HANDLE.isNotNull
            .and(
                PAGE_VIEW_EVENT.EVENT_PATH.eq(ownPath)
                    .or(PAGE_VIEW_EVENT.EVENT_PATH.like(ownPathWithQueryParam))
            )
    }

    suspend fun fetchHourlyPageViews(
        hours: Int,
        excludedUserIds: List<String>
    ): List<HourlyPageViewRecord> {
        // format: YYYY-MM-DD HH
        val hourExpression = "to_char(${PAGE_VIEW_EVENT.EVENT_TIME.name}, 'YYYY-MM-DD HH24')"
        val hourField = DSL.field(hourExpression).`as`("hour")

        // get the start of the current hour to exclude it
        val currentHourStart = Clock.System.now()
            .toUtcLocalDateTime()
            .withMinute(0).withSecond(0).withNano(0)
            .toUtcInstant()

        return dslContext
            .select(
                hourField,
                uniqueHourlyPageViewFields(hourExpression)
            )
            .from(PAGE_VIEW_EVENT)
            .where(PAGE_VIEW_EVENT.EVENT_TIME.isWithin(hours.hours))
            .and(PAGE_VIEW_EVENT.EVENT_TIME.lessThan(currentHourStart))
            .and(excludedUsersCondition(excludedUserIds))
            .groupBy(hourField)
            .orderBy(hourField.asc())
            .awaitRecords()
            .map { record ->
                HourlyPageViewRecord(
                    hour = record.getValue("hour", String::class.java),
                    uniquePageViews = record.getValue("unique_page_views", Int::class.java)
                )
            }
    }

    suspend fun fetchDailyPageViews(
        days: Int,
        excludedUserIds: List<String>
    ): List<DailyValueRecord> {
        val dayField = PAGE_VIEW_EVENT.EVENT_TIME.localDate("day")

        // get the start of the current day to exclude it
        val currentDayStart = Clock.System.now()
            .toUtcLocalDate()
            .atStartOfDay()
            .toUtcInstant()

        return dslContext
            .select(
                dayField,
                uniqueDailyPageViewsField()
            )
            .from(PAGE_VIEW_EVENT)
            .where(PAGE_VIEW_EVENT.EVENT_TIME.isWithin(days.days))
            .and(PAGE_VIEW_EVENT.EVENT_TIME.lessThan(currentDayStart))
            .and(excludedUsersCondition(excludedUserIds))
            .groupBy(dayField)
            .orderBy(dayField.asc())
            .awaitRecords()
            .map { record ->
                DailyValueRecord(
                    day = record.getValue("day", LocalDate::class.java),
                    value = record.getValue("unique_page_views", Int::class.java)
                )
            }
    }

    private companion object {

        // data collection starts ends of October 2025
        val startDate: Instant =
            instantOfUtc(2025, 11, 1, 0, 0, 0)

        fun excludedUsersCondition(excludedUserIds: List<String>): Condition {
            return if (excludedUserIds.isEmpty()) {
                DSL.noCondition()
            } else {
                PAGE_VIEW_EVENT.USER_ID.notIn(excludedUserIds)
            }
        }

        fun uniqueHourlyPageViewFields(hourExpression: String): Field<Int> {
            // create a composite field for counting unique user-hour combinations
            val userHourComposite = DSL.concat(
                PAGE_VIEW_EVENT.USER_ID,
                DSL.inline("-"),
                DSL.field(hourExpression)
            )

            return DSL.countDistinct(userHourComposite).`as`("unique_page_views")
        }

        fun uniqueDailyPageViewsField(): Field<Int> {
            val userDayCompositeField =
                DSL.concat(
                    PAGE_VIEW_EVENT.USER_ID,
                    DSL.inline("-"),
                    PAGE_VIEW_EVENT.EVENT_TIME.localDate(null)
                )

            return DSL.countDistinct(userDayCompositeField).`as`("unique_page_views")
        }

    }

}
