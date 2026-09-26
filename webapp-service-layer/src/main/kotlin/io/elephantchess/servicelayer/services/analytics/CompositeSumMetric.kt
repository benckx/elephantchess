package io.elephantchess.servicelayer.services.analytics

import io.elephantchess.db.model.IntDimensionValueRecord
import io.elephantchess.db.model.MonthlyValueRecord
import io.elephantchess.db.model.analytics.DailyValueRecord
import org.jooq.DSLContext

/**
 * A metric whose values are the per-period sum of a [base] hourly metric and one or more [additional]
 * metrics. Used to keep a metric continuous when part of its underlying data has been moved to a
 * separate table (e.g. "new guests" = live guests still in the `user` table + archived guests).
 *
 * Hourly values come from the [base] metric only, since archived data is always older than the hourly
 * window.
 */
class CompositeSumMetric(
    name: String,
    private val base: HourlyAvailableMetric,
    private val additional: List<Metric>,
) : HourlyAvailableMetric(name) {

    override suspend fun countByHour(dslContext: DSLContext, hours: Int): List<IntDimensionValueRecord> {
        return base.countByHour(dslContext, hours)
    }

    override suspend fun countByDay(dslContext: DSLContext, days: Int): List<DailyValueRecord> {
        val all = listOf(base.countByDay(dslContext, days)) + additional.map { it.countByDay(dslContext, days) }
        return all.flatten()
            .groupBy { it.day }
            .map { (day, records) -> DailyValueRecord(day, records.sumOf { it.value.toLong() }) }
            .sortedByDescending { it.day }
    }

    override suspend fun countByYearMonth(dslContext: DSLContext): List<MonthlyValueRecord> {
        val all = listOf(base.countByYearMonth(dslContext)) + additional.map { it.countByYearMonth(dslContext) }
        return all.flatten()
            .groupBy { it.month }
            .map { (month, records) -> MonthlyValueRecord(month, records.sumOf { it.value.toLong() }) }
            .sortedByDescending { it.month }
    }

    override suspend fun countByYear(dslContext: DSLContext): List<IntDimensionValueRecord> {
        return mergeIntDimension(listOf(base.countByYear(dslContext)) + additional.map { it.countByYear(dslContext) })
    }

    override suspend fun countByCentury(dslContext: DSLContext): List<IntDimensionValueRecord> {
        return mergeIntDimension(listOf(base.countByCentury(dslContext)) + additional.map { it.countByCentury(dslContext) })
    }

    private fun mergeIntDimension(all: List<List<IntDimensionValueRecord>>): List<IntDimensionValueRecord> {
        return all.flatten()
            .groupBy { it.period }
            .map { (period, records) -> IntDimensionValueRecord(period, records.sumOf { it.value.toLong() }) }
            .sortedByDescending { it.period }
    }

}
