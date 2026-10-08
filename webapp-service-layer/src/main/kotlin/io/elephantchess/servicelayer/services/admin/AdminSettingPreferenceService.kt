package io.elephantchess.servicelayer.services.admin

import io.elephantchess.utils.di.KoinSingleton

import io.elephantchess.db.model.SettingPreferenceMonthlyValueCountRecord
import io.elephantchess.db.services.SettingPreferenceEventDaoService
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.admin.SettingPreferenceStatsResponse

@KoinSingleton
class AdminSettingPreferenceService(
    private val settingPreferenceEventDaoService: SettingPreferenceEventDaoService
) {

    /**
     * Returns one stats segment per user type (AUTHENTICATED, GUEST) so the admin page can
     * display them separately side by side.
     */
    suspend fun fetchStats(): SettingPreferenceStatsResponse {
        val segments = UserType.entries.map { userType -> buildSegment(userType) }
        return SettingPreferenceStatsResponse(segments)
    }

    private suspend fun buildSegment(userType: UserType): SettingPreferenceStatsResponse.Segment {
        val userTypeName = userType.name

        val evolutionByField = settingPreferenceEventDaoService
            .listStringFieldMonthlyValueCounts(userTypeName)
            .groupBy { it.fieldName }

        val stringFields = settingPreferenceEventDaoService
            .listStringFieldStats(userTypeName)
            .map { field ->
                SettingPreferenceStatsResponse.StringFieldStats(
                    fieldName = field.fieldName,
                    nullCount = field.nullCount,
                    nonNullCount = field.nonNullCount,
                    values = field.values.map { SettingPreferenceStatsResponse.ValueCount(it.value!!, it.count) },
                    evolution = buildEvolution(evolutionByField[field.fieldName].orEmpty())
                )
            }

        val numberFields = settingPreferenceEventDaoService
            .listNumberFieldStats(userTypeName)
            .map { field ->
                SettingPreferenceStatsResponse.NumberFieldStats(
                    fieldName = field.fieldName,
                    nullCount = field.nullCount,
                    nonNullCount = field.nonNullCount,
                    min = field.min,
                    max = field.max,
                    avg = field.avg,
                    buckets = field.buckets.map { SettingPreferenceStatsResponse.Bucket(it.label, it.count) }
                )
            }

        return SettingPreferenceStatsResponse.Segment(
            userType = userTypeName,
            totalCount = settingPreferenceEventDaoService.countAll(userTypeName),
            stringFields = stringFields,
            numberFields = numberFields
        )
    }

    private fun buildEvolution(
        records: List<SettingPreferenceMonthlyValueCountRecord>
    ): SettingPreferenceStatsResponse.Evolution {
        val months = records.map { formatYearMonth(it.year, it.month) }.distinct().sorted()
        val totalByMonth = records
            .groupBy { formatYearMonth(it.year, it.month) }
            .mapValues { (_, monthRecords) -> monthRecords.sumOf { it.count } }

        val series = records
            .groupBy { it.value }
            .map { (value, valueRecords) ->
                val countByMonth = valueRecords.associate { formatYearMonth(it.year, it.month) to it.count }
                val shares = months.map { month ->
                    val total = totalByMonth[month] ?: 0L
                    if (total == 0L) 0.0 else countByMonth.getOrDefault(month, 0L) * 100.0 / total
                }
                SettingPreferenceStatsResponse.ValueShareSeries(value, shares)
            }
            .sortedByDescending { it.shares.sum() }

        return SettingPreferenceStatsResponse.Evolution(
            months = months,
            sampleSizes = months.map { totalByMonth[it] ?: 0L },
            series = series
        )
    }

    private fun formatYearMonth(year: Int, month: Int): String {
        return "%04d-%02d".format(year, month)
    }

}
