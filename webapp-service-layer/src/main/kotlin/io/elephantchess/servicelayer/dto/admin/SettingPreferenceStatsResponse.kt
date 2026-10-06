package io.elephantchess.servicelayer.dto.admin

data class SettingPreferenceStatsResponse(
    val segments: List<Segment>
) {

    /**
     * One user-type slice of the stats (e.g. AUTHENTICATED or GUEST).
     */
    data class Segment(
        val userType: String,
        val totalCount: Long,
        val stringFields: List<StringFieldStats>,
        val numberFields: List<NumberFieldStats>
    )

    data class StringFieldStats(
        val fieldName: String,
        val nullCount: Long,
        val nonNullCount: Long,
        val values: List<ValueCount>
    )

    data class NumberFieldStats(
        val fieldName: String,
        val nullCount: Long,
        val nonNullCount: Long,
        val min: Double?,
        val max: Double?,
        val avg: Double?,
        val buckets: List<Bucket>
    )

    data class ValueCount(
        val value: String,
        val count: Long
    )

    data class Bucket(
        val label: String,
        val count: Long
    )

}
