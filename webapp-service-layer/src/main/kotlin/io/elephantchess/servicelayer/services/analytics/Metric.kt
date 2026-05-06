package io.elephantchess.servicelayer.services.analytics

import io.elephantchess.db.model.IntDimensionValueRecord
import io.elephantchess.db.model.MonthlyValueRecord
import io.elephantchess.db.model.analytics.DailyValueRecord
import org.jooq.DSLContext

abstract class Metric(val name: String) {

    abstract suspend fun countByDay(dslContext: DSLContext, days: Int): List<DailyValueRecord>

    abstract suspend fun countByYearMonth(dslContext: DSLContext): List<MonthlyValueRecord>

    abstract suspend fun countByYear(dslContext: DSLContext): List<IntDimensionValueRecord>

    abstract suspend fun countByCentury(dslContext: DSLContext): List<IntDimensionValueRecord>

}
