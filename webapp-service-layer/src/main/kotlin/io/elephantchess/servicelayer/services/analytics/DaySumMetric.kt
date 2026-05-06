package io.elephantchess.servicelayer.services.analytics

import io.elephantchess.db.model.IntDimensionValueRecord
import io.elephantchess.db.model.MonthlyValueRecord
import io.elephantchess.db.model.analytics.DailyValueRecord
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.centuryOfDay
import io.elephantchess.db.utils.yearMonthOfDay
import io.elephantchess.db.utils.yearOfDay
import org.jooq.*
import org.jooq.impl.DSL
import java.math.BigDecimal
import java.time.LocalDate

class DaySumMetric<N : Number>(
    name: String,
    private val table: Table<out Record>,
    private val dateTimeField: Field<LocalDate>,
    private val measureField: Field<N>,
    private val condition: Condition? = null,
) : Metric(name) {

    override suspend fun countByDay(dslContext: DSLContext, days: Int): List<DailyValueRecord> {
        return countByTime(
            timeField = dateTimeField,
            measureField = measureField,
            dslContext = dslContext,
            table = table,
            condition = condition,
            limit = dateTimeField.ge(LocalDate.now().minusDays(days.toLong())).isTrue
        ).map { record -> DailyValueRecord.ofBigDecimal(record) }
    }

    override suspend fun countByYearMonth(dslContext: DSLContext): List<MonthlyValueRecord> {
        return countByTime(
            timeField = dateTimeField.yearMonthOfDay(),
            measureField = measureField,
            dslContext = dslContext,
            table = table,
            condition = condition
        ).map { record -> MonthlyValueRecord.ofBigDecimal(record) }
    }

    override suspend fun countByYear(dslContext: DSLContext): List<IntDimensionValueRecord> {
        return countByTime(
            timeField = dateTimeField.yearOfDay(),
            measureField = measureField,
            dslContext = dslContext,
            table = table,
            condition = condition
        ).map { record -> IntDimensionValueRecord.ofBigDecimal(record) }
    }

    override suspend fun countByCentury(dslContext: DSLContext): List<IntDimensionValueRecord> {
        return countByTime(
            timeField = dateTimeField.centuryOfDay(),
            measureField = measureField,
            dslContext = dslContext,
            table = table,
            condition = condition
        ).map { record -> IntDimensionValueRecord.ofBigDecimal(record) }
    }

    private suspend fun <R : Record, T> countByTime(
        timeField: Field<T>,
        measureField: Field<N>,
        dslContext: DSLContext,
        table: Table<R>,
        condition: Condition? = null,
        limit: Condition? = null,
    ): List<Record2<T, BigDecimal>> {
        val conditions = mutableListOf(condition, limit).filterNotNull()

        val query1 =
            dslContext
                .select(timeField, DSL.sum(measureField))
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
