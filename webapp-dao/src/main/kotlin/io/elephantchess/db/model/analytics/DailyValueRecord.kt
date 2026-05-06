package io.elephantchess.db.model.analytics

import org.jooq.Record2
import java.math.BigDecimal
import java.time.LocalDate

data class DailyValueRecord(
    val day: LocalDate,
    val value: Number,
) {

    companion object {

        fun ofInt(record2: Record2<LocalDate, Int>): DailyValueRecord {
            return DailyValueRecord(record2.value1(), record2.value2())
        }

        fun ofBigDecimal(record2: Record2<LocalDate, BigDecimal>): DailyValueRecord {
            return DailyValueRecord(record2.value1(), record2.value2())
        }

    }

}
