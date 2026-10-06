package io.elephantchess.servicelayer.services.admin

import io.elephantchess.utils.di.KoinSingleton

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

        val stringFields = settingPreferenceEventDaoService
            .listStringFieldStats(userTypeName)
            .map { field ->
                SettingPreferenceStatsResponse.StringFieldStats(
                    fieldName = field.fieldName,
                    nullCount = field.nullCount,
                    nonNullCount = field.nonNullCount,
                    values = field.values.map { SettingPreferenceStatsResponse.ValueCount(it.value!!, it.count) }
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

}
