package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.model.PuzzleLeaderboardRecord
import io.elephantchess.db.services.PuzzleResultDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.minusHours
import io.elephantchess.db.utils.rating
import io.elephantchess.model.GameType
import io.elephantchess.model.TimeControlCategory
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.servicelayer.dto.global.*
import io.elephantchess.servicelayer.services.GlobalAnalyticsService.Companion.CacheKey.*
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRate
import io.github.oshai.kotlinlogging.KLogger
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Data shown on the "Global" page
 */
class GlobalAnalyticsService(
    private val userService: UserService,
    private val userDaoService: UserDaoService,
    private val puzzleResultDaoService: PuzzleResultDaoService,
    private val puzzleCache: PuzzleCache,
    private val gameDataService: GameDataService,
    private val podService: PodService,
    appConfig: AppConfig,
    refresherScope: CoroutineScope,
    private val logger: KLogger
) {

    private val isDockerized = appConfig.isDockerized

    private val shortRefresh = 20.minutes
    private val longRefresh = 4.hours

    private val shortCache =
        Cache
            .Builder<CacheKey, Any>()
            .expireAfterWrite(shortRefresh)
            .build()

    private val longCache =
        Cache
            .Builder<CacheKey, Any>()
            .expireAfterWrite(longRefresh)
            .build()

    private val refreshJob = launchAtFixedRate(
        scope = refresherScope,
        initialDelay = 5.seconds,
        period = longRefresh / 2
    ) {
        logger.info { "refreshing global analytics caches..." }

        // overwrite cached values with freshly computed ones (no empty window)
        val gameStats = computeGlobalGameStats().also { longCache.put(GLOBAL_GAME_STATS, it) }
        val appData = computeGlobalAppData().also { longCache.put(GLOBAL_APP_DATA, it) }
        val userStats = computeGlobalUserStats().also { longCache.put(USER_ANALYTICS, it) }
        val puzzleStats = computeGlobalPuzzleStats().also { longCache.put(GLOBAL_PUZZLE_STATS, it) }
        logger.debug { "scheduled refresh: $gameStats" }
        logger.debug { "scheduled refresh: $appData" }
        logger.debug { "scheduled refresh: $userStats" }
        logger.debug { "scheduled refresh: $puzzleStats" }
    }

    fun cancel() {
        refreshJob.cancel()
    }

    suspend fun fetchPlayerVsPlayerLeaderboard(): GlobalPlayerVsPlayerLeaderboardResponse =
        shortCache.typedGet(PVP_LEADERBOARD) {
            val highestUserPerCategory =
                userDaoService
                    .fetchUsersWithHighestRating(numberOfUsers = 5)
                    .filterNot { (timeControlCategory, _) ->
                        timeControlCategory == TimeControlCategory.SEVERAL_DAYS
                    }

            val allUserIds = highestUserPerCategory.flatMap { (_, users) -> users.map { it.id } }.distinct()
            val numberOfGamesAndLastPlayed = userDaoService.fetchNumberOfGamesAndLastPlayed(allUserIds)

            highestUserPerCategory
                .flatMap { (timeControlCategory, users) ->
                    users
                        .sortedByDescending { user ->
                            user.rating(timeControlCategory)
                        }
                        .map { user ->
                            val aggregatedInfo =
                                numberOfGamesAndLastPlayed.find { record ->
                                    record.userId == user.id && record.category == timeControlCategory
                                }

                            GlobalPlayerVsPlayerLeaderboardResponse.Entry(
                                category = timeControlCategory,
                                userId = user.id,
                                username = user.handle,
                                countryCode = user.country,
                                rating = user.rating(timeControlCategory),
                                totalPlayed = aggregatedInfo?.totalPlayed ?: 0,
                                lastPlayed = aggregatedInfo?.lastPlayed?.toEpochMilliseconds() ?: 0L
                            )
                        }
                }
                .let { entries ->
                    GlobalPlayerVsPlayerLeaderboardResponse(entries)
                }
        }

    suspend fun fetchPuzzleLeaderboard(maxNumberOfEntries: Int): PuzzleStatsLeaderboardResponse =
        shortCache.typedGet(PUZZLE_LEADERBOARD) {
            mapPuzzleLeaderboardRecords(userDaoService.fetchPuzzleLeaderboard(maxNumberOfEntries))
        }

    suspend fun fetchPuzzleLeaderboardLast12Months(maxNumberOfEntries: Int): PuzzleStatsLeaderboardResponse =
        shortCache.typedGet(PUZZLE_LEADERBOARD_LAST_12_MONTHS) {
            mapPuzzleLeaderboardRecords(userDaoService.fetchPuzzleLeaderboard(maxNumberOfEntries, days = 365))
        }

    suspend fun fetchGlobalPuzzleStats(): GlobalPuzzleStatsResponse =
        longCache.typedGet(GLOBAL_PUZZLE_STATS) {
            computeGlobalPuzzleStats()
        }

    private fun computeGlobalPuzzleStats(): GlobalPuzzleStatsResponse {
        val totalPuzzles = puzzleCache.countAll()
        if (totalPuzzles == 0) {
            throw IllegalStateException("Cannot compute global puzzle stats while puzzle cache is empty")
        }

        val puzzlesPlayedAtLeast10x = puzzleCache.countPuzzlePlayedAtLeast(10)
        val puzzlesPlayedAtLeast20x = puzzleCache.countPuzzlePlayedAtLeast(20)
        val puzzlesPlayedRatio10x = puzzlesPlayedAtLeast10x.toFloat() / totalPuzzles.toFloat()
        val puzzlesPlayedRatio20x = puzzlesPlayedAtLeast20x.toFloat() / totalPuzzles.toFloat()

        return GlobalPuzzleStatsResponse(
            totalPuzzles = totalPuzzles,
            totalPuzzlesPlayed = puzzleCache.countAllAttempts(),
            puzzlesPlayedRatio10x = puzzlesPlayedRatio10x,
            puzzlesPlayedRatio20x = puzzlesPlayedRatio20x,
        )
    }

    suspend fun fetchGlobalGameStats(): GlobalGameStatsResponse =
        longCache.typedGet(GLOBAL_GAME_STATS) {
            computeGlobalGameStats()
        }

    private suspend fun computeGlobalGameStats(): GlobalGameStatsResponse {
        val totalGames = gameDataService.countTotalGames()
        val totalInAppGames = gameDataService.countTotalAppGames()

        val totalMovesMap = gameDataService.countTotalMoves()
        val totalMoves = totalMovesMap.values.sum()
        val totalInAppMovesMap = totalMovesMap.filter { (key, _) -> key != GameType.DB }.values.sum()

        return GlobalGameStatsResponse(
            totalGames = totalGames,
            totalInAppGames = totalInAppGames,
            totalMoves = totalMoves,
            totalInAppMoves = totalInAppMovesMap,
            totalManchuGames = gameDataService.countTotalManchuGames()
        )
    }

    suspend fun fetchGlobalUserStats(): GlobalUserStatsResponse {
        val response: GlobalUserStatsResponse =
            longCache.typedGet(USER_ANALYTICS) {
                computeGlobalUserStats()
            }

        // we don't cache "countOnline"
        return response.copy(onlineUsers = userService.countOnline())
    }

    private suspend fun computeGlobalUserStats(): GlobalUserStatsResponse {
        val minGuestDuration = 30.minutes
        val recentlyDuration = 14.days

        val totalUsers =
            userDaoService.countAuthenticated() +
                    userDaoService.countGuestsWithSessionAtLeast(minGuestDuration)

        val totalRecentlyActive =
            userDaoService.countActiveRecently(recentlyDuration, listOf(AUTHENTICATED)) +
                    userDaoService.countGuestsWithSessionAtLeastAndActiveWithin(
                        minGuestDuration,
                        recentlyDuration
                    )

        return GlobalUserStatsResponse(
            totalUsers = totalUsers,
            recentlyActiveUsers = totalRecentlyActive,
            onlineUsers = 0
        )
    }

    suspend fun fetchGlobalAppData(): GlobalAppDataResponse =
        longCache.typedGet(GLOBAL_APP_DATA) {
            computeGlobalAppData()
        }

    private fun computeGlobalAppData(): GlobalAppDataResponse {
        val lastDeploy =
            if (isDockerized) {
                podService.getLastRedeployTime() ?: Clock.System.now()
            } else {
                Clock.System.now().minusHours(4L)
            }

        return GlobalAppDataResponse(
            lastDeploy = lastDeploy.toEpochMilliseconds()
        )
    }

    private suspend fun mapPuzzleLeaderboardRecords(records: List<PuzzleLeaderboardRecord>): PuzzleStatsLeaderboardResponse {
        if (records.isEmpty()) {
            return PuzzleStatsLeaderboardResponse(emptyList())
        }

        val userIds = records.map { record -> record.userId }
        val countsByUserId = puzzleResultDaoService.fetchPuzzleResultCounts(userIds).associateBy { it.userId }

        return records
            .map { record ->
                val userCounts = countsByUserId[record.userId]
                val total = userCounts?.total ?: 0
                val solved = userCounts?.solved ?: 0
                val failed = userCounts?.failed ?: 0
                val solvedRate = if (total == 0) 0.0 else solved.toDouble() / total.toDouble()
                val failedRate = if (total == 0) 0.0 else failed.toDouble() / total.toDouble()

                PuzzleStatsLeaderboardResponse.Entry(
                    username = record.username,
                    countryCode = record.countryCode,
                    last = record.currentRating,
                    max = record.maxRating,
                    total = record.totalPlayed,
                    lastPlayed = record.lastPlayed.toEpochMilliseconds(),
                    solvedRate = solvedRate,
                    failedRate = failedRate
                )
            }
            .let { entries ->
                PuzzleStatsLeaderboardResponse(entries)
            }
    }

    // to avoid casting
    private suspend inline fun <reified T> Cache<CacheKey, Any>.typedGet(
        key: CacheKey,
        noinline loader: suspend () -> Any
    ): T {
        return get(key) { loader() } as T
    }

    private companion object {

        enum class CacheKey {
            PVP_LEADERBOARD,
            PUZZLE_LEADERBOARD,
            PUZZLE_LEADERBOARD_LAST_12_MONTHS,
            GLOBAL_PUZZLE_STATS,
            GLOBAL_GAME_STATS,
            USER_ANALYTICS,
            GLOBAL_APP_DATA
        }

    }

}
