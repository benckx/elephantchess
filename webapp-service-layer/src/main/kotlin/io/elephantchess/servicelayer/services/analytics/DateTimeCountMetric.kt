package io.elephantchess.servicelayer.services.analytics

import io.elephantchess.db.model.IntDimensionValueRecord
import io.elephantchess.db.model.MonthlyValueRecord
import io.elephantchess.db.model.analytics.DailyValueRecord
import io.elephantchess.db.utils.*
import org.jooq.*
import org.jooq.impl.DSL
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class DateTimeCountMetric(
    name: String,
    private val table: Table<out Record>,
    private val dateTimeField: Field<Instant>,
    private val condition: Condition? = null,
) : HourlyAvailableMetric(name) {

    override suspend fun countByHour(dslContext: DSLContext, hours: Int): List<IntDimensionValueRecord> {
        return countByTime(
            timeField = dateTimeField.hourOfDay(),
            dslContext = dslContext,
            table = table,
            condition = condition,
            limit = dateTimeField.isWithin(hours.hours).isTrue
        ).map { record -> IntDimensionValueRecord.ofInt(record) }
    }

    override suspend fun countByDay(dslContext: DSLContext, days: Int): List<DailyValueRecord> {
        return countByTime(
            timeField = dateTimeField.localDate(),
            dslContext = dslContext,
            table = table,
            condition = condition,
            limit = dateTimeField.isWithin(days.days).isTrue
        ).map { record -> DailyValueRecord.ofInt(record) }
    }

    override suspend fun countByYearMonth(dslContext: DSLContext): List<MonthlyValueRecord> {
        return countByTime(
            timeField = dateTimeField.yearMonth(),
            dslContext = dslContext,
            table = table,
            condition = condition
        ).map { record -> MonthlyValueRecord.ofInt(record) }
    }

    override suspend fun countByYear(dslContext: DSLContext): List<IntDimensionValueRecord> {
        return countByTime(
            timeField = dateTimeField.year(),
            dslContext = dslContext,
            table = table,
            condition = condition
        ).map { record -> IntDimensionValueRecord.ofInt(record) }
    }

    override suspend fun countByCentury(dslContext: DSLContext): List<IntDimensionValueRecord> {
        return countByTime(
            timeField = dateTimeField.century(),
            dslContext = dslContext,
            table = table,
            condition = condition
        ).map { record -> IntDimensionValueRecord.ofInt(record) }
    }

    private suspend fun <R : Record, T> countByTime(
        timeField: Field<T>,
        dslContext: DSLContext,
        table: Table<R>,
        condition: Condition? = null,
        limit: Condition? = null,
    ): List<Record2<T, Int>> {
        val conditions = mutableListOf(condition, limit).filterNotNull()

        val query1 =
            dslContext
                .select(timeField, DSL.count())
                .from(table)

        val query2 =
            if (conditions.isNotEmpty()) {
                query1
                    .where(conditions)
                    .groupBy(timeField)
                    .orderBy(timeField.desc())
            } else {
                query1
                    .groupBy(timeField)
                    .orderBy(timeField.desc())
            }

        return query2.awaitRecords().toList()
    }

}
