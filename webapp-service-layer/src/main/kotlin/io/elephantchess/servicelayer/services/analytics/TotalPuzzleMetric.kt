package io.elephantchess.servicelayer.services.analytics

import io.elephantchess.db.dao.codegen.Tables.PUZZLE_RESULT
import io.elephantchess.db.dao.codegen.Tables.PUZZLE_RESULT_ANONYMOUS
import io.elephantchess.db.model.IntDimensionValueRecord
import io.elephantchess.db.model.MonthlyValueRecord
import io.elephantchess.db.model.analytics.DailyValueRecord
import io.elephantchess.db.utils.*
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record2
import org.jooq.impl.DSL
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration.Companion.hours

class TotalPuzzleMetric : HourlyAvailableMetric("puzzles") {

    override suspend fun countByHour(dslContext: DSLContext, hours: Int): List<IntDimensionValueRecord> {
        val timeField1 = PUZZLE_RESULT.ENTRY_CREATION.hourOfDay()
        val timeField2 = PUZZLE_RESULT_ANONYMOUS.ENTRY_CREATION.hourOfDay()
        val duration = hours.hours

        val select1 = dslContext
            .select(timeField1, DSL.count())
            .from(PUZZLE_RESULT)
            .where(PUZZLE_RESULT.ENTRY_CREATION.isWithin(duration))
            .groupBy(timeField1)
            .orderBy(timeField1.desc())

        val select2 = dslContext
            .select(timeField2, DSL.count())
            .from(PUZZLE_RESULT_ANONYMOUS)
            .where(PUZZLE_RESULT_ANONYMOUS.ENTRY_CREATION.isWithin(duration))
            .groupBy(timeField2)
            .orderBy(timeField2.desc())

        val records = select1.awaitRecords() + select2.awaitRecords().toList()

        return records
            .groupBy { it.get(0).toString().toInt() }
            .map { (date, records) ->
                IntDimensionValueRecord(date, records.sumOf { it.get(1).toString().toInt() })
            }
            .sortedByDescending { it.period }
    }

    override suspend fun countByDay(dslContext: DSLContext, days: Int): List<DailyValueRecord> {
        val records = countByTimeFields(
            PUZZLE_RESULT.ENTRY_CREATION.localDate(),
            PUZZLE_RESULT_ANONYMOUS.ENTRY_CREATION.localDate(),
            dslContext,
            days
        )

        return records
            .groupBy { LocalDate.parse(it.get(0).toString()) }
            .map { (date, records) ->
                DailyValueRecord(date, records.sumOf { it.get(1).toString().toInt() })
            }
            .sortedByDescending { it.day }
    }

    override suspend fun countByYearMonth(dslContext: DSLContext): List<MonthlyValueRecord> {
        val records = countByTimeFields(
            PUZZLE_RESULT.ENTRY_CREATION.yearMonth(),
            PUZZLE_RESULT_ANONYMOUS.ENTRY_CREATION.yearMonth(),
            dslContext
        )

        return records
            .groupBy { YearMonth.parse(it.get(0).toString()) }
            .map { (yearMonth, records) ->
                MonthlyValueRecord(yearMonth, records.sumOf { it.get(1).toString().toInt() })
            }
            .sortedByDescending { it.month }
    }

    override suspend fun countByYear(dslContext: DSLContext): List<IntDimensionValueRecord> {
        return countByIntPeriod(
            dslContext,
            PUZZLE_RESULT.ENTRY_CREATION.year(),
            PUZZLE_RESULT_ANONYMOUS.ENTRY_CREATION.year()
        )
    }

    override suspend fun countByCentury(dslContext: DSLContext): List<IntDimensionValueRecord> {
        return countByIntPeriod(
            dslContext,
            PUZZLE_RESULT.ENTRY_CREATION.century(),
            PUZZLE_RESULT_ANONYMOUS.ENTRY_CREATION.century()
        )
    }

    private suspend fun <T> countByIntPeriod(
        dslContext: DSLContext,
        timeField1: Field<T>,
        timeField2: Field<T>,
    ): List<IntDimensionValueRecord> {
        return countByTimeFields(timeField1, timeField2, dslContext)
            .groupBy { it.get(0).toString().toInt() }
            .map { (period, records) ->
                IntDimensionValueRecord(period, records.sumOf { it.get(1).toString().toInt() })
            }
            .sortedByDescending { it.period }
    }

    private suspend fun <T> countByTimeFields(
        timeField1: Field<T>,
        timeField2: Field<T>,
        dslContext: DSLContext,
        limit: Int? = null,
    ): List<Record2<T, Int>> {
        val select1 = dslContext
            .select(timeField1, DSL.count())
            .from(PUZZLE_RESULT)
            .groupBy(timeField1)
            .orderBy(timeField1.desc())

        val select2 = dslContext
            .select(timeField2, DSL.count())
            .from(PUZZLE_RESULT_ANONYMOUS)
            .groupBy(timeField2)
            .orderBy(timeField2.desc())

        return if (limit != null) {
            select1.limit(limit).awaitRecords() +
                    select2.limit(limit).awaitRecords()
        } else {
            select1.awaitRecords() + select2.awaitRecords()
        }
    }

}
