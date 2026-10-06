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
     * @param userType when set (AUTHENTICATED or GUEST), stats are restricted to that user type.
     *                 When null, all events are considered.
     */
    suspend fun fetchStats(userType: UserType? = null): SettingPreferenceStatsResponse {
        val userTypeName = userType?.name

        val stringFields = settingPreferenceEventDaoService
            .listStringFieldStats(userTypeName)
            // when filtering on a user type, its own distribution chart is redundant
            .filter { userType == null || it.fieldName != USER_TYPE_FIELD }
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

        return SettingPreferenceStatsResponse(
            userType = userTypeName,
            totalCount = settingPreferenceEventDaoService.countAll(userTypeName),
            stringFields = stringFields,
            numberFields = numberFields
        )
    }

    private companion object {
        private const val USER_TYPE_FIELD = "user_type"
    }

}
