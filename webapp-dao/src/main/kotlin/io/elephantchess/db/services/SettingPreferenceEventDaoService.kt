package io.elephantchess.db.services

import io.elephantchess.utils.di.KoinSingleton

import io.elephantchess.db.dao.codegen.Tables.SETTING_PREFERENCE_EVENT
import io.elephantchess.db.dao.codegen.tables.daos.SettingPreferenceEventDao
import io.elephantchess.db.dao.codegen.tables.pojos.SettingPreferenceEvent
import io.elephantchess.db.dao.codegen.tables.records.SettingPreferenceEventRecord
import io.elephantchess.db.model.FieldValueCountRecord
import io.elephantchess.db.model.SettingPreferenceNumberBucketRecord
import io.elephantchess.db.model.SettingPreferenceNumberFieldStatsRecord
import io.elephantchess.db.model.SettingPreferenceStringFieldStatsRecord
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.awaitSingleRecord
import io.elephantchess.db.utils.awaitSingleValue
import io.elephantchess.db.utils.insertReactive
import org.jooq.DSLContext
import org.jooq.TableField
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.math.ceil
import kotlin.math.floor

@KoinSingleton
class SettingPreferenceEventDaoService(private val dslContext: DSLContext) {

    suspend fun save(record: SettingPreferenceEvent) {
        dslContext.transactionCoroutine { cfg ->
            SettingPreferenceEventDao(cfg).insertReactive(record)
        }
    }

    suspend fun countAll(): Long {
        return dslContext
            .selectCount()
            .from(SETTING_PREFERENCE_EVENT)
            .awaitSingleValue<Int>()
            ?.toLong()
            ?: 0L
    }

    /**
     * Null-vs-not-null counts and non-null value distribution for every (string) preference field.
     */
    suspend fun listStringFieldStats(): List<SettingPreferenceStringFieldStatsRecord> {
        return STRING_FIELDS.map { field ->
            val countField = DSL.count()
            val distribution = dslContext
                .select(field, countField)
                .from(SETTING_PREFERENCE_EVENT)
                .groupBy(field)
                .orderBy(countField.desc())
                .awaitRecords()
                .map { record -> FieldValueCountRecord(record.get(field), record.get(countField).toLong()) }

            val nullCount = distribution.firstOrNull { it.value == null }?.count ?: 0L

            SettingPreferenceStringFieldStatsRecord(
                fieldName = field.name.lowercase(),
                nullCount = nullCount,
                nonNullCount = distribution.filter { it.value != null }.sumOf { it.count },
                values = distribution.filter { it.value != null }
            )
        }
    }

    /**
     * Null-vs-not-null counts and a bucketed histogram for every numeric preference field
     * (stored as text, parsed as numeric for aggregation).
     */
    suspend fun listNumberFieldStats(): List<SettingPreferenceNumberFieldStatsRecord> {
        return NUMBER_FIELDS.map { field ->
            val numeric = field.cast(SQLDataType.NUMERIC)
            val nullCountField = DSL.count().filterWhere(field.isNull)
            val nonNullCountField = DSL.count().filterWhere(field.isNotNull)
            val minField = DSL.min(numeric)
            val maxField = DSL.max(numeric)
            val avgField = DSL.avg(numeric)

            val stats = dslContext
                .select(nullCountField, nonNullCountField, minField, maxField, avgField)
                .from(SETTING_PREFERENCE_EVENT)
                .awaitSingleRecord()

            val nonNullCount = stats?.get(nonNullCountField)?.toLong() ?: 0L
            val min = stats?.get(minField)?.toDouble()
            val max = stats?.get(maxField)?.toDouble()

            SettingPreferenceNumberFieldStatsRecord(
                fieldName = field.name.lowercase(),
                nullCount = stats?.get(nullCountField)?.toLong() ?: 0L,
                nonNullCount = nonNullCount,
                min = min,
                max = max,
                avg = stats?.get(avgField)?.toDouble(),
                buckets = fetchBuckets(field, nonNullCount, min, max)
            )
        }
    }

    private suspend fun fetchBuckets(
        field: TableField<SettingPreferenceEventRecord, String>,
        nonNullCount: Long,
        min: Double?,
        max: Double?
    ): List<SettingPreferenceNumberBucketRecord> {
        if (nonNullCount == 0L || min == null || max == null) {
            return emptyList()
        }

        val minValue = floor(min).toLong()
        val maxValue = ceil(max).toLong()
        val span = maxValue - minValue
        val width = maxOf(1L, ceil((span + 1).toDouble() / BUCKET_COUNT).toLong())

        val numeric = field.cast(SQLDataType.NUMERIC)
        val bucketIndexField = DSL
            .floor(numeric.minus(minValue).div(width))
            .cast(SQLDataType.INTEGER)
        val countField = DSL.count()

        return dslContext
            .select(bucketIndexField, countField)
            .from(SETTING_PREFERENCE_EVENT)
            .where(field.isNotNull)
            .groupBy(bucketIndexField)
            .orderBy(bucketIndexField)
            .awaitRecords()
            .map { record ->
                val index = record.get(bucketIndexField) ?: 0
                val low = minValue + index * width
                val high = low + width - 1
                val label = if (width == 1L) low.toString() else "$low\u2013$high"
                SettingPreferenceNumberBucketRecord(label, record.get(countField).toLong())
            }
    }

    private companion object {

        private const val BUCKET_COUNT = 20

        private val STRING_FIELDS: List<TableField<SettingPreferenceEventRecord, String>> = listOf(
            SETTING_PREFERENCE_EVENT.USER_TYPE,
            SETTING_PREFERENCE_EVENT.PIECE_STYLE,
            SETTING_PREFERENCE_EVENT.SHOW_COORDINATES,
            SETTING_PREFERENCE_EVENT.MOVE_FORMAT,
            SETTING_PREFERENCE_EVENT.MOVE_NODE_EVAL_FORMAT,
            SETTING_PREFERENCE_EVENT.SHOW_ANALYTICS_ARROWS,
            SETTING_PREFERENCE_EVENT.COORDINATES_STYLE,
            SETTING_PREFERENCE_EVENT.FLIP_OPPONENT_PIECES,
            SETTING_PREFERENCE_EVENT.PLAY_SOUNDS,
            SETTING_PREFERENCE_EVENT.COLORBLIND_FRIENDLY_BLACK_PIECES
        )

        private val NUMBER_FIELDS: List<TableField<SettingPreferenceEventRecord, String>> = listOf(
            SETTING_PREFERENCE_EVENT.MOVE_TREE_WIDGET_HEIGHT_PVP,
            SETTING_PREFERENCE_EVENT.MOVE_TREE_WIDGET_HEIGHT_PVB,
            SETTING_PREFERENCE_EVENT.MOVE_TREE_WIDGET_HEIGHT_SIMPLE_BOARD,
            SETTING_PREFERENCE_EVENT.MOVE_TREE_WIDGET_HEIGHT_ANALYSIS,
            SETTING_PREFERENCE_EVENT.MOVE_TREE_WIDGET_HEIGHT_DATABASE_VIEWER
        )

    }

}
