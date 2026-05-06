package io.elephantchess.db.model

import org.jooq.Record7
import kotlin.time.Instant

data class AnalysisAndUserRecord(private val record: Record7<String, Instant, Instant, String, Int, String, String>) {

    fun analysisId(): String = record.value1()
    fun created(): Instant = record.value2()
    fun lastUpdated(): Instant = record.value3()
    fun analysisName(): String = record.value4()
    fun versionNumber(): Int = record.value5()
    fun userId(): String = record.value6()
    fun username(): String = record.value7()

}
