package io.elephantchess.db.model

import org.jooq.Record2
import java.math.BigDecimal
import java.time.YearMonth

data class MonthlyValueRecord(
    val month: YearMonth,
    val value: Number,
) {

    companion object {

        fun ofInt(record2: Record2<YearMonth, Int>): MonthlyValueRecord {
            return MonthlyValueRecord(record2.value1(), record2.value2())
        }

        fun ofBigDecimal(record2: Record2<YearMonth, BigDecimal>): MonthlyValueRecord {
            return MonthlyValueRecord(record2.value1(), record2.value2())
        }

    }

}
