package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.PuzzleResultDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.model.Engine
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.admin.*
import io.elephantchess.servicelayer.services.GameDataService
import io.elephantchess.servicelayer.services.GameDataService.Companion.MIN_MOVE_INDEX
import io.elephantchess.servicelayer.services.UserCache
import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.servicelayer.services.analytics.MIN_GENUINE_GUEST_LIFESPAN_SECONDS
import io.elephantchess.utils.di.KoinSingleton
import io.elephantchess.xiangqi.Variant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@KoinSingleton
class AdminOverviewService(
    private val userDaoService: UserDaoService,
    private val pvbGameDaoService: PlayerVsBotGameDaoService,
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    private val puzzleResultDaoService: PuzzleResultDaoService,
    private val gameDataService: GameDataService,
    private val userService: UserService,
    private val userCache: UserCache,
) {

    suspend fun listOnlineUsers(): OnlineUsersResponse {
        val entries = userService
            .onlineUserIds()
            .mapNotNull { userId -> userCache.get(userId) }
            .map { user -> OnlineUsersResponse.Entry(user.userId, user.username, user.userType) }
            .sortedBy { entry -> entry.username.lowercase() }

        return OnlineUsersResponse(entries)
    }

    suspend fun listOnlineWithinHours(hours: Int): RecentlyOnlineUsersResponse {
        val duration = (hours * 60).minutes

        val authenticatedUsers =
            userDaoService
                .listRecentlyActiveMinutes(hours * 60, listOf(UserType.AUTHENTICATED))
                .map { record ->
                    val username = userCache.fetchUsernameOrDefault(record.id)
                    RecentlyOnlineUsersResponse.Entry(record.id, username)
                }
                .sortedBy { entry -> entry.username.lowercase() }

        val guestCount = userDaoService.countActiveRecently(
            duration = duration,
            userTypes = listOf(UserType.GUEST),
            minSessionSeconds = MIN_GENUINE_GUEST_LIFESPAN_SECONDS,
        )

        return RecentlyOnlineUsersResponse(authenticatedUsers, guestCount)
    }

    suspend fun fetchLatestPvpActivity(): LatestPvpActivityResponse {
        val latestPvpActivity =
            listOfNotNull(
                pvpGameDaoService.latestGameActivity(),
                pvpGameDaoService.latestMoveTime(),
            )
                .maxOrNull()
                ?.toEpochMilliseconds()

        val latestPvp3Activity =
            listOfNotNull(
                pvpGameDaoService.latestGameActivity(MIN_MOVE_INDEX),
                pvpGameDaoService.latestMoveTime(MIN_MOVE_INDEX),
            )
                .maxOrNull()
                ?.toEpochMilliseconds()

        return LatestPvpActivityResponse(
            latestPvpActivity = latestPvpActivity,
            latestPvp3Activity = latestPvp3Activity,
            latestManchu3Activity = latestPvp3Activity(Variant.MANCHU),
            latestXiangqi3Activity = latestPvp3Activity(Variant.XIANGQI),
        )
    }

    suspend fun fetchLatestPvbActivity(): LatestPvbActivityResponse {
        val latestPvbActivity =
            listOfNotNull(
                pvbGameDaoService.latestGameActivity(),
                pvbGameDaoService.latestMoveTime(),
            )
                .maxOrNull()
                ?.toEpochMilliseconds()

        val latestPvb3Activity =
            listOfNotNull(
                pvbGameDaoService.latestGameActivity(MIN_MOVE_INDEX),
                pvbGameDaoService.latestMoveTime(MIN_MOVE_INDEX),
            )
                .maxOrNull()
                ?.toEpochMilliseconds()

        return LatestPvbActivityResponse(
            latestPvbActivity = latestPvbActivity,
            latestPvb3Activity = latestPvb3Activity,
            latestFairy3Activity = latestPvb3Activity(engine = Engine.FAIRYSTOCKFISH),
            latestManchu3Activity = latestPvb3Activity(variant = Variant.MANCHU),
            latestJanggi3Activity = latestPvb3Activity(variant = Variant.JANGGI),
            latestXiangqi3Activity = latestPvb3Activity(variant = Variant.XIANGQI),
        )
    }

    private suspend fun latestPvp3Activity(variant: Variant): Long? =
        listOfNotNull(
            pvpGameDaoService.latestGameActivity(MIN_MOVE_INDEX, variant),
            pvpGameDaoService.latestMoveTime(MIN_MOVE_INDEX, variant),
        ).maxOrNull()?.toEpochMilliseconds()

    private suspend fun latestPvb3Activity(engine: Engine? = null, variant: Variant? = null): Long? =
        listOfNotNull(
            pvbGameDaoService.latestGameActivity(MIN_MOVE_INDEX, engine, variant),
            pvbGameDaoService.latestMoveTime(MIN_MOVE_INDEX, engine, variant),
        ).maxOrNull()?.toEpochMilliseconds()

    suspend fun fetchLivePvpGames(): LivePvpGamesResponse =
        LivePvpGamesResponse(
            pvpGameDaoService.countLiveGames(
                lastUpdated = liveGameDuration,
                userIds = userService.onlineUserIds(),
            ),
        )

    suspend fun fetchLivePvbGames(): LivePvbGamesResponse =
        LivePvbGamesResponse(
            pvbGameDaoService.countLiveGames(
                lastUpdated = liveGameDuration,
                userIds = userService.onlineUserIds(),
            ),
        )

    suspend fun fetchLatestPuzzleActivity(): LatestPuzzleActivityResponse =
        LatestPuzzleActivityResponse(
            latestPlayedPuzzle = puzzleResultDaoService.latestPlayedPuzzle()?.toEpochMilliseconds(),
            latestPuzzleVote = puzzleResultDaoService.latestPuzzleVote()?.toEpochMilliseconds(),
        )

    suspend fun fetchLatestAnalysisActivity(): LatestAnalysisActivityResponse =
        LatestAnalysisActivityResponse(
            latestMoveAnalysis = gameDataService.latestMoveAnalysisUpdate()?.toEpochMilliseconds(),
        )

    suspend fun fetchLatestNewUsers(): LatestNewUsersResponse =
        LatestNewUsersResponse(
            latestNewGuest = userDaoService.latestNewGuestUser()?.toEpochMilliseconds(),
            latestNewAuthenticatedUser = userDaoService.latestNewAuthenticatedUser()?.toEpochMilliseconds(),
        )

    suspend fun fetchOverviewTrends(): OverviewTrendResponse {
        val now = Clock.System.now()
        val latest30DaysStart = now - 30.days
        val previous30DaysStart = latest30DaysStart - 30.days

        val latest30DaysGames =
            pvpGameDaoService.countGamesOverMoveIndexByCreatedBetween(MIN_MOVE_INDEX, latest30DaysStart, now) +
                pvbGameDaoService.countGamesOverMoveIndexByCreatedBetween(MIN_MOVE_INDEX, latest30DaysStart, now)
        val previous30DaysGames =
            pvpGameDaoService.countGamesOverMoveIndexByCreatedBetween(MIN_MOVE_INDEX, previous30DaysStart, latest30DaysStart) +
                pvbGameDaoService.countGamesOverMoveIndexByCreatedBetween(MIN_MOVE_INDEX, previous30DaysStart, latest30DaysStart)

        val latest30DaysPuzzles = puzzleResultDaoService.countPlayedBetween(latest30DaysStart, now)
        val previous30DaysPuzzles = puzzleResultDaoService.countPlayedBetween(previous30DaysStart, latest30DaysStart)

        return OverviewTrendResponse(
            gamesOver3 = OverviewTrendResponse.TrendValue(
                latest30Days = latest30DaysGames,
                previous30Days = previous30DaysGames,
            ),
            puzzles = OverviewTrendResponse.TrendValue(
                latest30Days = latest30DaysPuzzles,
                previous30Days = previous30DaysPuzzles,
            ),
        )
    }

    private companion object {

        private val liveGameDuration = 3.minutes
    }
}
