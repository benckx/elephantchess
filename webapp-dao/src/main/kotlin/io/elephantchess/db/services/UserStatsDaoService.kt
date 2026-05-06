package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.STATS_ONLINE_USERS_MINUTES
import io.elephantchess.db.dao.codegen.tables.daos.StatsOnlineUsersDaysDao
import io.elephantchess.db.dao.codegen.tables.daos.StatsOnlineUsersMinutesDao
import io.elephantchess.db.dao.codegen.tables.pojos.StatsOnlineUsersDays
import io.elephantchess.db.dao.codegen.tables.pojos.StatsOnlineUsersMinutes
import io.elephantchess.db.model.OnlineUsersStatsByDayOfWeek
import io.elephantchess.db.model.OnlineUsersStatsDaily
import io.elephantchess.db.model.OnlineUsersStatsHourlyRecord
import io.elephantchess.db.model.OnlineUsersStatsMonthly
import io.elephantchess.db.utils.*
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.time.YearMonth
import kotlin.time.Clock

class UserStatsDaoService(private val dslContext: DSLContext) {

    suspend fun save(entry: StatsOnlineUsersDays) {
        dslContext.transactionCoroutine { cfg ->
            StatsOnlineUsersDaysDao(cfg).insertReactive(entry)
        }
    }

    suspend fun save(entry: StatsOnlineUsersMinutes) {
        dslContext.transactionCoroutine { cfg ->
            StatsOnlineUsersMinutesDao(cfg).insertReactive(entry)
        }
    }

    suspend fun fetchOnlineUsersStatsByHour(): List<OnlineUsersStatsHourlyRecord> {
        val table = STATS_ONLINE_USERS_MINUTES
        val thirtyDaysAgo = Clock.System.now().minusDays(30)

        val hourField = DSL.extract(table.MEASURED_TIME, org.jooq.DatePart.HOUR)
        val totalField = table.AUTHENTICATED_USERS_5MIN.plus(table.GUESTS_USERS_5MIN)

        return dslContext
            .select(
                hourField.`as`("hour_of_day"),
                DSL.min(totalField).`as`("min_total"),
                DSL.max(totalField).`as`("max_total"),
                DSL.avg(totalField).`as`("avg_total")
            )
            .from(table)
            .where(table.MEASURED_TIME.ge(thirtyDaysAgo))
            .groupBy(hourField)
            .orderBy(hourField)
            .awaitMappedRecords()
    }

    suspend fun fetchOnlineUsersStatsByDay(): List<OnlineUsersStatsDaily> {
        val table = STATS_ONLINE_USERS_MINUTES
        val thirtyDaysAgo = Clock.System.now().minusDays(30)

        val dayField = table.MEASURED_TIME.localDate()
        val totalField = table.AUTHENTICATED_USERS_5MIN.plus(table.GUESTS_USERS_5MIN)

        return dslContext
            .select(
                dayField,
                DSL.min(totalField).`as`("min_total"),
                DSL.max(totalField).`as`("max_total"),
                DSL.avg(totalField).`as`("avg_total")
            )
            .from(table)
            .where(table.MEASURED_TIME.ge(thirtyDaysAgo))
            .groupBy(dayField)
            .orderBy(dayField)
            .awaitMappedRecords()
    }

    suspend fun fetchOnlineUsersStatsByDayOfWeek(): List<OnlineUsersStatsByDayOfWeek> {
        val table = STATS_ONLINE_USERS_MINUTES
        val thirtyDaysAgo = Clock.System.now().minusDays(30)

        val dowField = DSL.extract(table.MEASURED_TIME, org.jooq.DatePart.DAY_OF_WEEK)
        val totalField = table.AUTHENTICATED_USERS_5MIN.plus(table.GUESTS_USERS_5MIN)

        return dslContext
            .select(
                dowField.`as`("day_of_week"),
                DSL.min(totalField).`as`("min_total"),
                DSL.max(totalField).`as`("max_total"),
                DSL.avg(totalField).`as`("avg_total")
            )
            .from(table)
            .where(table.MEASURED_TIME.ge(thirtyDaysAgo))
            .groupBy(dowField)
            .orderBy(dowField)
            .awaitMappedRecords()
    }

    suspend fun fetchOnlineUsersStatsByMonth(months: Int = 18): List<OnlineUsersStatsMonthly> {
        val table = STATS_ONLINE_USERS_MINUTES
        val monthsAgo = Clock.System.now().minusMonths(months.toLong())

        val yearField = DSL.extract(table.MEASURED_TIME, org.jooq.DatePart.YEAR)
        val monthField = DSL.extract(table.MEASURED_TIME, org.jooq.DatePart.MONTH)
        val totalField = table.AUTHENTICATED_USERS_5MIN.plus(table.GUESTS_USERS_5MIN)

        return dslContext
            .select(
                yearField.`as`("year"),
                monthField.`as`("month"),
                DSL.min(totalField).`as`("min_total"),
                DSL.max(totalField).`as`("max_total"),
                DSL.avg(totalField).`as`("avg_total")
            )
            .from(table)
            .where(table.MEASURED_TIME.ge(monthsAgo))
            .groupBy(yearField, monthField)
            .orderBy(yearField, monthField)
            .awaitRecords()
            .map { record ->
                OnlineUsersStatsMonthly(
                    month = YearMonth.of(record.get("year", Int::class.java), record.get("month", Int::class.java)),
                    minTotal = record.get("min_total", Int::class.java),
                    maxTotal = record.get("max_total", Int::class.java),
                    avgTotal = record.get("avg_total", java.math.BigDecimal::class.java).toInt()
                )
            }
    }

}
