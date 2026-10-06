package io.elephantchess.servicelayer.services.admin

import io.elephantchess.utils.di.KoinSingleton

import io.elephantchess.db.services.SettingPreferenceEventDaoService
import io.elephantchess.servicelayer.dto.admin.SettingPreferenceStatsResponse

@KoinSingleton
class AdminSettingPreferenceService(
    private val settingPreferenceEventDaoService: SettingPreferenceEventDaoService
) {

    suspend fun fetchStats(): SettingPreferenceStatsResponse {
        val stringFields = settingPreferenceEventDaoService
            .listStringFieldStats()
            .map { field ->
                SettingPreferenceStatsResponse.StringFieldStats(
                    fieldName = field.fieldName,
                    nullCount = field.nullCount,
                    nonNullCount = field.nonNullCount,
                    values = field.values.map { SettingPreferenceStatsResponse.ValueCount(it.value!!, it.count) }
                )
            }

        val numberFields = settingPreferenceEventDaoService
            .listNumberFieldStats()
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

        return SettingPreferenceStatsResponse(
            totalCount = settingPreferenceEventDaoService.countAll(),
            stringFields = stringFields,
            numberFields = numberFields
        )
    }

}
