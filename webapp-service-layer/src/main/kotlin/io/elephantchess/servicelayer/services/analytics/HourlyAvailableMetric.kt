package io.elephantchess.servicelayer.services.analytics

import io.elephantchess.db.model.IntDimensionValueRecord
import org.jooq.DSLContext

abstract class HourlyAvailableMetric(name: String) : Metric(name) {

    abstract suspend fun countByHour(dslContext: DSLContext, hours: Int): List<IntDimensionValueRecord>

}
