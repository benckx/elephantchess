package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.model.PuzzleLeaderboardRecord
import io.elephantchess.db.services.PuzzleDaoService
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
    private val puzzleDaoService: PuzzleDaoService,
    private val puzzleCache: PuzzleCache,
    private val gameDataService: GameDataService,
    private val podService: PodService,
    appConfig: AppConfig,
    refresherScope: CoroutineScope,
    private val logger: KLogger
) {

    private val isDockerized = appConfig.isDockerized

    private val shortRefresh = 20.minutes
    private val longRefresh = 6.hours

    private val shortCache =
        Cache
            .Builder<CacheKey, Any>()
            .expireAfterAccess(shortRefresh)
            .build()

    private val longCache =
        Cache
            .Builder<CacheKey, Any>()
            .expireAfterAccess(longRefresh)
            .build()

    private val refreshJob = launchAtFixedRate(
        scope = refresherScope,
        initialDelay = 5.seconds,
        period = longRefresh / 3
    ) {
        logger.debug { "refreshing global analytics caches..." }
        val gameStats = fetchGlobalGameStats()
        val appData = fetchGlobalAppData()
        val userStats = fetchGlobalUserStats()
        logger.debug { "scheduled refresh: $gameStats" }
        logger.debug { "scheduled refresh: $appData" }
        logger.debug { "scheduled refresh: $userStats" }
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
            val totalPuzzles = puzzleCache.countAllEnabled()
            val puzzlesPlayedAtLeast10x = puzzleCache.countPuzzlePlayedAtLeastAmongEnabled(10)
            val puzzlesPlayedAtLeast20x = puzzleCache.countPuzzlePlayedAtLeastAmongEnabled(20)
            val puzzlesPlayedRatio10x = puzzlesPlayedAtLeast10x.toFloat() / totalPuzzles.toFloat()
            val puzzlesPlayedRatio20x = puzzlesPlayedAtLeast20x.toFloat() / totalPuzzles.toFloat()

            GlobalPuzzleStatsResponse(
                totalPuzzles = totalPuzzles,
                totalPuzzlesPlayed = puzzleCache.countAllAttempts(),
                puzzlesPlayedRatio10x = puzzlesPlayedRatio10x,
                puzzlesPlayedRatio20x = puzzlesPlayedRatio20x,
            )
        }

    suspend fun fetchGlobalGameStats(): GlobalGameStatsResponse =
        longCache.typedGet(GLOBAL_GAME_STATS) {
            val totalGames = gameDataService.countTotalGames()
            val totalInAppGames = gameDataService.countTotalAppGames()

            val totalMovesMap = gameDataService.countTotalMoves()
            val totalMoves = totalMovesMap.values.sum()
            val totalInAppMovesMap = totalMovesMap.filter { (key, _) -> key != GameType.DB }.values.sum()

            GlobalGameStatsResponse(
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

                GlobalUserStatsResponse(
                    totalUsers = totalUsers,
                    recentlyActiveUsers = totalRecentlyActive,
                    onlineUsers = 0
                )
            }

        // we don't cache "countOnline"
        return response.copy(onlineUsers = userService.countOnline())
    }

    suspend fun fetchGlobalAppData(): GlobalAppDataResponse =
        longCache.typedGet(GLOBAL_APP_DATA) {
            val lastDeploy =
                if (isDockerized) {
                    podService.getLastRedeployTime() ?: Clock.System.now()
                } else {
                    Clock.System.now().minusHours(4L)
                }

            GlobalAppDataResponse(
                lastDeploy = lastDeploy.toEpochMilliseconds()
            )
        }

    private suspend fun mapPuzzleLeaderboardRecords(records: List<PuzzleLeaderboardRecord>): PuzzleStatsLeaderboardResponse {
        val userIds = records.map { record -> record.userId }
        // TODO: could be done in 1 SQL query
        val solvedCount = puzzleDaoService.fetchPuzzleSolved(userIds)
        val failedCount = puzzleDaoService.fetchPuzzleFailed(userIds)
        val totalCount = puzzleDaoService.fetchPuzzleTotal(userIds)

        return records
            .map { record ->
                val total = totalCount.find { it.value1() == record.userId }?.value2() ?: 0
                val solved = solvedCount.find { it.value1() == record.userId }?.value2() ?: 0
                val failed = failedCount.find { it.value1() == record.userId }?.value2() ?: 0
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
