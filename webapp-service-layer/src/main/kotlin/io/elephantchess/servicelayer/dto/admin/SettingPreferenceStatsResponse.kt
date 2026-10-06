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
        val values: List<ValueCount>,
        val evolution: Evolution
    )

    /**
     * Monthly evolution of the share of each non-null value. [months] holds the x-axis labels
     * (e.g. "2026-01"); each series' shares (percentages) align with [months]. Shares are used
     * rather than raw counts because the sampling rate changes over time.
     */
    data class Evolution(
        val months: List<String>,
        val sampleSizes: List<Long>,
        val series: List<ValueShareSeries>
    )

    data class ValueShareSeries(
        val value: String,
        val shares: List<Double>
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
