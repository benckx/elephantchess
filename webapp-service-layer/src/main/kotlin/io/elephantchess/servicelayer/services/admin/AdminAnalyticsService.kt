package io.elephantchess.servicelayer.services.admin

import io.elephantchess.config.AppConfig
import io.elephantchess.db.model.IntDimensionValueRecord
import io.elephantchess.db.model.MonthlyValueRecord
import io.elephantchess.db.model.analytics.DailyValueRecord
import io.elephantchess.db.model.analytics.MonthlyPageViewRecord
import io.elephantchess.db.services.AnalysisDaoService
import io.elephantchess.db.services.PageViewEventDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.UserStatsDaoService
import io.elephantchess.model.GameJoinSource
import io.elephantchess.servicelayer.dto.admin.*
import io.elephantchess.servicelayer.services.GameDataService.Companion.MIN_MOVE_INDEX
import io.elephantchess.servicelayer.services.analytics.HourlyAvailableMetric
import io.elephantchess.servicelayer.services.analytics.allMetrics
import io.elephantchess.utils.rangeOfDays
import io.elephantchess.utils.rangeOfYearMonths
import io.github.oshai.kotlinlogging.KLogger
import org.jooq.DSLContext
import java.time.LocalDate
import java.time.YearMonth

class AdminAnalyticsService(
    private val analysisDaoService: AnalysisDaoService,
    private val userStatsDaoService: UserStatsDaoService,
    private val pageViewEventDaoService: PageViewEventDaoService,
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    private val dslContext: DSLContext,
    logger: KLogger,
    appConfig: AppConfig,
) {

    private val excludedUserIds = appConfig.excludedFromAnalytics

    init {
        logger.info { "excludedUserIds = $excludedUserIds" }
    }

    /**
     * Limit to 6 hours for now, so order is not an issue
     * (assuming we don't check before 6 am UTC)
     */
    suspend fun fetchHourlyStats(): MultipleTimeSeriesResponse {
        val allTimeSeries = allMetrics
            .filterIsInstance<HourlyAvailableMetric>()
            .map { metrics -> metrics.name to metrics.countByHour(dslContext, hours = 6) }

        val hours = allTimeSeries
            .flatMap { (_, records) -> records }
            .map { record -> record.period }
            .distinct()
            .sorted()

        return if (hours.isNotEmpty()) {
            val min = hours.first()
            val max = hours.last()

            val timeSeries = allTimeSeries
                .map { (name, records) -> name to fillGapsWithZeros(min, max, records) }
                .map { (name, records) -> TimeSeries(name, records.map { it.value }) }

            MultipleTimeSeriesResponse(hours.map { it.toString() }, timeSeries)
        } else {
            MultipleTimeSeriesResponse(emptyList(), emptyList())
        }
    }

    suspend fun fetchDailyStats(numberOfDays: Int = 28): MultipleTimeSeriesResponse {
        val allTimeSeries = allMetrics.map { metrics ->
            metrics.name to metrics.countByDay(dslContext, days = numberOfDays)
        }

        val days = allTimeSeries
            .flatMap { (_, records) -> records }
            .map { record -> record.day }
            .distinct()
            .sorted()

        val expectedStart = LocalDate.now().minusDays(numberOfDays.toLong() - 1)
        val actualStart = days.firstOrNull() ?: expectedStart
        val actualEnd = days.lastOrNull() ?: LocalDate.now()

        val requestedStart = maxOf(actualStart, expectedStart)
        val requestedEnd = actualEnd

        val timeSeries = allTimeSeries
            .map { (name, records) -> name to fillGapsWithZeros(actualStart, actualEnd, records) }
            .map { (name, records) -> name to records.filter { it.day >= requestedStart && it.day <= requestedEnd } }
            .map { (name, records) -> TimeSeries(name, records.map { it.value }) }

        return MultipleTimeSeriesResponse(
            periods = rangeOfDays(requestedStart, requestedEnd).map { it.toString() },
            timeSeries = timeSeries
        )
    }

    suspend fun fetchMonthlyStats(): MultipleTimeSeriesResponse {
        val allTimeSeries = allMetrics.map { metrics ->
            metrics.name to metrics.countByYearMonth(dslContext)
        }

        val months = allTimeSeries
            .flatMap { (_, records) -> records }
            .map { record -> record.month }
            .distinct()
            .sorted()

        val min = months.first()
        val max = months.last()

        val timeSeries = allTimeSeries
            .map { (name, records) -> name to fillGapsWithZeros(min, max, records) }
            .map { (name, records) -> TimeSeries(name, records.map { it.value }) }

        return MultipleTimeSeriesResponse(months.map { it.toString() }, timeSeries)
    }

    suspend fun fetchYearlyStats(): MultipleTimeSeriesResponse {
        val allTimeSeries = allMetrics.map { metrics ->
            metrics.name to metrics.countByYear(dslContext)
        }

        val years = allTimeSeries
            .flatMap { (_, records) -> records }
            .map { record -> record.period }
            .distinct()
            .sorted()

        val min = years.first()
        val max = years.last()

        val timeSeries = allTimeSeries
            .map { (name, records) -> name to fillGapsWithZeros(min, max, records) }
            .map { (name, records) -> TimeSeries(name, records.map { it.value }) }

        return MultipleTimeSeriesResponse(years.map { it.toString() }, timeSeries)
    }

    suspend fun fetchTotalStats(): MultipleTimeSeriesResponse {
        val allTimeSeries = allMetrics.map { metrics ->
            metrics.name to metrics.countByCentury(dslContext)
        }

        val periods = allTimeSeries
            .flatMap { (_, records) -> records }
            .map { record -> record.period }
            .distinct()

        val timeSeries =
            allTimeSeries
                .map { (name, records) ->
                    TimeSeries(name, records.map { it.value }.reversed())
                }

        return MultipleTimeSeriesResponse(periods.map { it.toString() }, timeSeries)
    }

    suspend fun fetchDailyAvgByMonthStats(): MultipleTimeSeriesResponse {
        val response = fetchMonthlyStats()
        val size = response.timeSeries.size
        val currentYearMonth = YearMonth.now()
        val currentDayOfMonth = LocalDate.now().dayOfMonth

        val newTimeSeries = (0 until size).map { i ->
            val timeSeries = response.timeSeries[i]
            val newValues = (0 until response.periods.size).map { j ->
                val period = response.periods[j]
                val yearMonth = YearMonth.parse(period)
                val nbrOfDays = if (yearMonth == currentYearMonth) currentDayOfMonth else yearMonth.lengthOfMonth()
                timeSeries.values[j].toDouble() / nbrOfDays
            }

            TimeSeries(timeSeries.name, newValues)
        }

        return MultipleTimeSeriesResponse(response.periods, newTimeSeries)
    }

    suspend fun fetchAnalysisPerUser(): AnalysisPerUserResponse {
        val lastUpdatedByUser =
            analysisDaoService
                .fetchLastUpdatePerUser()
                .associate { record2 ->
                    record2.value1()!! to record2.value2().toEpochMilliseconds()
                }

        val entries =
            analysisDaoService
                .countAnalysisPerUser(20)
                .map { record3 ->
                    AnalysisPerUserResponse.Entry(
                        userId = record3.value1(),
                        username = record3.value2(),
                        count = record3.value3(),
                        lastUpdated = lastUpdatedByUser[record3.value1()]
                    )
                }
                .sortedByDescending { entry ->
                    entry.lastUpdated
                }

        return AnalysisPerUserResponse(entries)
    }

    suspend fun fetchOnlineUsersStatsByHour(): OnlineUsersStatsByHourResponse {
        return userStatsDaoService
            .fetchOnlineUsersStatsByHour()
            .map { record ->
                OnlineUsersStatsByHourResponse.Entry(
                    hourOfDay = record.hourOfDay,
                    minTotal = record.minTotal,
                    maxTotal = record.maxTotal,
                    avgTotal = record.avgTotal
                )
            }
            .let { entries ->
                OnlineUsersStatsByHourResponse(entries)
            }
    }

    suspend fun fetchOnlineUsersStatsByDay(): OnlineUsersStatsByDayResponse {
        return userStatsDaoService
            .fetchOnlineUsersStatsByDay()
            .map { record ->
                OnlineUsersStatsByDayResponse.Entry(
                    day = record.day.toString(),
                    minTotal = record.minTotal,
                    maxTotal = record.maxTotal,
                    avgTotal = record.avgTotal
                )
            }
            .let { entries ->
                OnlineUsersStatsByDayResponse(entries)
            }
    }

    suspend fun fetchOnlineUsersStatsByDayOfWeek(): OnlineUsersStatsByDayOfWeekResponse {
        return userStatsDaoService
            .fetchOnlineUsersStatsByDayOfWeek()
            .map { record ->
                OnlineUsersStatsByDayOfWeekResponse.Entry(
                    dayOfWeek = record.dayOfWeek,
                    minTotal = record.minTotal,
                    maxTotal = record.maxTotal,
                    avgTotal = record.avgTotal
                )
            }
            .let { entries ->
                OnlineUsersStatsByDayOfWeekResponse(entries)
            }
    }

    suspend fun fetchOnlineUsersStatsByMonth(months: Int): OnlineUsersStatsByMonthResponse {
        return userStatsDaoService
            .fetchOnlineUsersStatsByMonth(months)
            .map { record ->
                OnlineUsersStatsByMonthResponse.Entry(
                    month = record.month.toString(),
                    minTotal = record.minTotal,
                    maxTotal = record.maxTotal,
                    avgTotal = record.avgTotal
                )
            }
            .let { entries ->
                OnlineUsersStatsByMonthResponse(entries)
            }
    }

    suspend fun fetchPageViewStatsByGad(): MultipleTimeSeriesResponse {
        val pattern = "%?gad_source=1%"
        val records = pageViewEventDaoService.fetchMonthlyPageViewsByPattern(pattern, excludedUserIds)
        return mapPageViewRecordsToMultipleTimeseries(records)
    }

    suspend fun fetchPageViewStatsByEventPath(eventPath: String): MultipleTimeSeriesResponse {
        val records = pageViewEventDaoService.fetchMonthlyPageViews(eventPath, excludedUserIds)
        return mapPageViewRecordsToMultipleTimeseries(records)
    }

    suspend fun fetchPageViewStatsForDatabaseGames(): MultipleTimeSeriesResponse {
        val records = pageViewEventDaoService.fetchMonthlyDatabaseGamePageViews(excludedUserIds)
        return mapPageViewRecordsToMultipleTimeseries(records)
    }

    suspend fun fetchPageViewStatsForOwnUserProfiles(): MultipleTimeSeriesResponse {
        val records = pageViewEventDaoService.fetchMonthlyOwnUserProfilePageViews(excludedUserIds)
        return mapPageViewRecordsToMultipleTimeseries(records)
    }

    suspend fun fetchPageViewStatsForOtherUserProfiles(): MultipleTimeSeriesResponse {
        val records = pageViewEventDaoService.fetchMonthlyOtherUserProfilePageViews(excludedUserIds)
        return mapPageViewRecordsToMultipleTimeseries(records)
    }

    suspend fun fetchHourlyPageViews(hours: Int = 12): HourlyPageViewsResponse {
        val records = pageViewEventDaoService.fetchHourlyPageViews(
            hours = hours,
            excludedUserIds = excludedUserIds
        )

        val entries = records.map { record ->
            HourlyPageViewsResponse.Entry(
                hour = record.hour,
                pageViews = record.uniquePageViews
            )
        }

        return HourlyPageViewsResponse(entries)
    }

    suspend fun fechDailyPageViews(days: Int = 30): DailyPageViewsResponse {
        val records = pageViewEventDaoService.fetchDailyPageViews(
            days = days,
            excludedUserIds = excludedUserIds
        )

        val entries = records.map { record ->
            DailyPageViewsResponse.Entry(
                day = record.day.toString(),
                pageViews = record.value.toInt()
            )
        }

        return DailyPageViewsResponse(entries)
    }

    /**
     * Fetches monthly PvP stats with breakdown by [GameJoinSource]
     *
     * Returns:
     * - Total PvP games per month
     * - PvP > 3 games per month (with MIN_MOVE_INDEX condition)
     * - Breakdown of PvP > 3 by join source
     */
    suspend fun fetchPvpStatsByJoinSource(): PvpJoinSourceStatsResponse {
        // Query total PvP games by month
        val totalPvpByMonth = pvpGameDaoService
            .countTotalGamesByMonth()
            .associate { it.month to it.value.toInt() }

        // Query PvP > 3 games by month
        val pvpOver3ByMonth = pvpGameDaoService
            .countGamesOverMoveIndexByMonth(MIN_MOVE_INDEX)
            .associate { it.month to it.value.toInt() }

        // Query PvP > 3 by join source and month
        val pvpOver3BySourceAndMonth = pvpGameDaoService
            .countGamesOverMoveIndexByMonthAndJoinSource(MIN_MOVE_INDEX)
            .groupBy { it.month }
            .mapValues { (_, records) ->
                records.associate { it.joinSource to it.count }
            }

        // Get all months from all queries
        val allMonths = (totalPvpByMonth.keys + pvpOver3ByMonth.keys + pvpOver3BySourceAndMonth.keys)
            .distinct()
            .sorted()

        if (allMonths.isEmpty()) {
            return PvpJoinSourceStatsResponse(emptyList(), emptyList(), emptyList())
        }

        val firstMonth = allMonths.first()
        val lastMonth = allMonths.last()
        val allPeriods = rangeOfYearMonths(firstMonth, lastMonth)

        // Build percentage time series (PvP > 3 / Total PvP * 100)
        val percentageValues = allPeriods.map { month ->
            val total = totalPvpByMonth[month] ?: 0
            val over3 = pvpOver3ByMonth[month] ?: 0
            if (total > 0) (over3.toDouble() / total * 100) else 0.0
        }

        // Build breakdown by join source
        val allSources = GameJoinSource.entries.map { it.name } + "UNKNOWN"
        val joinSourceSeries = allSources.map { source ->
            val values = allPeriods.map { month ->
                pvpOver3BySourceAndMonth[month]?.get(source) ?: 0
            }
            TimeSeries(source, values)
        }.filter { series -> series.values.any { (it as Int) > 0 } } // Only include sources with data

        return PvpJoinSourceStatsResponse(
            periods = allPeriods.map { it.toString() },
            percentageOver3 = percentageValues,
            joinSourceBreakdown = joinSourceSeries
        )
    }

    private fun mapPageViewRecordsToMultipleTimeseries(records: List<MonthlyPageViewRecord>): MultipleTimeSeriesResponse {
        if (records.isEmpty()) {
            return MultipleTimeSeriesResponse(emptyList(), emptyList())
        }

        // Normalize paths: if path contains "gad_source=1", crop everything after "gad_source=1"
        val normalizedRecords = normalizeRecords(records)
        val recordsByPath = groupByEventPath(normalizedRecords)

        // Get all year-months from all records
        val allYearMonths = normalizedRecords.map { it.yearMonth }.distinct().sorted()
        val firstYearMonth = allYearMonths.first()
        val lastYearMonth = allYearMonths.last()

        // Create time series for each event path
        val timeSeries = recordsByPath.map { (path, pathRecords) ->
            // Create a map for quick lookup
            val recordsByYearMonth = pathRecords.associateBy { it.yearMonth }

            // Fill gaps with zeros
            val values = rangeOfYearMonths(firstYearMonth, lastYearMonth).map { yearMonth ->
                recordsByYearMonth[yearMonth]?.uniquePageViews ?: 0L
            }

            TimeSeries(path, values.map { it.toDouble() })
        }

        // Return periods in YYYY-MM format to make it clear it's year-month data
        val periods = rangeOfYearMonths(firstYearMonth, lastYearMonth).map { it.toString() }

        return MultipleTimeSeriesResponse(periods, timeSeries)
    }

    private companion object {

        fun fillGapsWithZeros(
            minMonth: YearMonth,
            maxMonth: YearMonth,
            records: List<MonthlyValueRecord>,
        ): List<MonthlyValueRecord> {
            return rangeOfYearMonths(minMonth, maxMonth).map { month ->
                records.find { it.month == month }
                    ?: MonthlyValueRecord(month, 0)
            }
        }

        fun fillGapsWithZeros(
            firstDay: LocalDate,
            lastDay: LocalDate,
            records: List<DailyValueRecord>,
        ): List<DailyValueRecord> {
            return rangeOfDays(firstDay, lastDay).map { day ->
                records.find { it.day == day }
                    ?: DailyValueRecord(day, 0)
            }
        }

        fun fillGapsWithZeros(
            first: Int,
            last: Int,
            records: List<IntDimensionValueRecord>,
        ): List<IntDimensionValueRecord> {
            return (first..last).map { period ->
                records.find { it.period == period }
                    ?: IntDimensionValueRecord(period, 0)
            }
        }

        // Normalize paths: if path contains "gad_source=1" or "fbclid", crop everything after it
        fun normalizeRecords(records: List<MonthlyPageViewRecord>): List<MonthlyPageViewRecord> {
            return records.map { record ->
                val normalizedPath = when {
                    record.label.contains("gad_source=1") -> {
                        val index = record.label.indexOf("gad_source=1")
                        record.label.substring(0, index + "gad_source=1".length)
                    }

                    record.label.contains("fbclid") -> {
                        val index = record.label.indexOf("fbclid")
                        record.label.substring(0, index + "fbclid".length)
                    }

                    else -> record.label
                }
                record.copy(label = normalizedPath)
            }
        }

        // Group by normalized eventPath and sum page views for same month+path
        fun groupByEventPath(normalizedRecords: List<MonthlyPageViewRecord>): Map<String, List<MonthlyPageViewRecord>> {
            return normalizedRecords
                .groupBy { it.label }
                .mapValues { (_, pathRecords) ->
                    pathRecords
                        .groupBy { YearMonth.from(it.yearMonth) }
                        .map { (_, monthRecords) ->
                            monthRecords.first().copy(
                                uniquePageViews = monthRecords.sumOf { it.uniquePageViews }
                            )
                        }
                }
        }

    }

}
