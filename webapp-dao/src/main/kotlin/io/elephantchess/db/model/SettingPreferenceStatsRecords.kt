package io.elephantchess.db.model

data class FieldValueCountRecord(
    val value: String?,
    val count: Long
)

data class SettingPreferenceStringFieldStatsRecord(
    val fieldName: String,
    val nullCount: Long,
    val nonNullCount: Long,
    val values: List<FieldValueCountRecord>
)

data class SettingPreferenceNumberBucketRecord(
    val label: String,
    val count: Long
)

data class SettingPreferenceNumberFieldStatsRecord(
    val fieldName: String,
    val nullCount: Long,
    val nonNullCount: Long,
    val min: Double?,
    val max: Double?,
    val avg: Double?,
    val buckets: List<SettingPreferenceNumberBucketRecord>
)
