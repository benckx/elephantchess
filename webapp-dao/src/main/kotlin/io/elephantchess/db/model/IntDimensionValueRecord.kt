package io.elephantchess.db.model

import org.jooq.Record2
import java.math.BigDecimal

data class IntDimensionValueRecord(val period: Int, val value: Number) {

    companion object {

        fun ofInt(record2: Record2<Int, Int>): IntDimensionValueRecord = IntDimensionValueRecord(record2.value1(), record2.value2())

        fun ofBigDecimal(record2: Record2<Int, BigDecimal>): IntDimensionValueRecord =
            IntDimensionValueRecord(record2.value1(), record2.value2())
    }
}
