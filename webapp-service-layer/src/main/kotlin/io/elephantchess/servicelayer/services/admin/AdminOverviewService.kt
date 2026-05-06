package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.PuzzleResultDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.servicelayer.dto.admin.*
import io.elephantchess.servicelayer.services.GameDataService
import io.elephantchess.servicelayer.services.GameDataService.Companion.MIN_MOVE_INDEX
import io.elephantchess.servicelayer.services.UserCache
import kotlin.time.Duration.Companion.minutes

class AdminOverviewService(
    private val userDaoService: UserDaoService,
    private val pvbGameDaoService: PlayerVsBotGameDaoService,
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    private val puzzleResultDaoService: PuzzleResultDaoService,
    private val gameDataService: GameDataService,
    private val userCache: UserCache,
) {

    suspend fun listOnlineUsers(): OnlineUsersResponse {
        return userDaoService
            .listRecentlyActiveSeconds(20)
            .map { record ->
                val username = userCache.fetchUsernameOrDefault(record.id)
                OnlineUsersResponse.Entry(record.id, username, record.userType)
            }
            .sortedBy { entry -> entry.username.lowercase() }
            .let { entries ->
                OnlineUsersResponse(entries)
            }
    }

    suspend fun listOnlineWithinHours(hours: Int): OnlineUsersResponse {
        val entries =
            userDaoService
                .listRecentlyActiveMinutes(hours * 60)
                .map { record ->
                    val username = userCache.fetchUsernameOrDefault(record.id)
                    OnlineUsersResponse.Entry(record.id, username, record.userType)
                }
                .sortedBy { entry -> entry.username.lowercase() }

        return OnlineUsersResponse(entries)
    }


    suspend fun fetchLatestPvpActivity(): LatestPvpActivityResponse {
        val latestPvpActivity =
            listOfNotNull(
                pvpGameDaoService.latestGameActivity(),
                pvpGameDaoService.latestMoveTime()
            )
                .maxOrNull()
                ?.toEpochMilliseconds()

        val latestPvp3Activity =
            listOfNotNull(
                pvpGameDaoService.latestGameActivity(MIN_MOVE_INDEX),
                pvpGameDaoService.latestMoveTime(MIN_MOVE_INDEX)
            )
                .maxOrNull()
                ?.toEpochMilliseconds()

        return LatestPvpActivityResponse(
            latestPvpActivity = latestPvpActivity,
            latestPvp3Activity = latestPvp3Activity,
        )
    }

    suspend fun fetchLatestPvbActivity(): LatestPvbActivityResponse {
        val latestPvbActivity =
            listOfNotNull(
                pvbGameDaoService.latestGameActivity(),
                pvbGameDaoService.latestMoveTime()
            )
                .maxOrNull()
                ?.toEpochMilliseconds()

        val latestPvb3Activity =
            listOfNotNull(
                pvbGameDaoService.latestGameActivity(MIN_MOVE_INDEX),
                pvbGameDaoService.latestMoveTime(MIN_MOVE_INDEX)
            )
                .maxOrNull()
                ?.toEpochMilliseconds()

        return LatestPvbActivityResponse(
            latestPvbActivity = latestPvbActivity,
            latestPvb3Activity = latestPvb3Activity,
        )
    }

    suspend fun fetchLivePvpGames(): LivePvpGamesResponse {
        val liveGameDuration = 3.minutes
        return LivePvpGamesResponse(
            livePvpGames = pvpGameDaoService.countLiveGames(liveGameDuration),
        )
    }

    suspend fun fetchLivePvbGames(): LivePvbGamesResponse {
        val liveGameDuration = 3.minutes
        return LivePvbGamesResponse(
            livePvbGames = pvbGameDaoService.countLiveGames(liveGameDuration),
        )
    }

    suspend fun fetchLatestPuzzleActivity(): LatestPuzzleActivityResponse {
        return LatestPuzzleActivityResponse(
            latestPlayedPuzzle = puzzleResultDaoService.latestPlayedPuzzle()?.toEpochMilliseconds(),
            latestPuzzleVote = puzzleResultDaoService.latestPuzzleVote()?.toEpochMilliseconds(),
        )
    }

    suspend fun fetchLatestAnalysisActivity(): LatestAnalysisActivityResponse {
        return LatestAnalysisActivityResponse(
            latestMoveAnalysis = gameDataService.latestMoveAnalysisUpdate()?.toEpochMilliseconds(),
        )
    }

    suspend fun fetchLatestNewUsers(): LatestNewUsersResponse {
        return LatestNewUsersResponse(
            latestNewGuest = userDaoService.latestNewGuestUser()?.toEpochMilliseconds(),
            latestNewAuthenticatedUser = userDaoService.latestNewAuthenticatedUser()?.toEpochMilliseconds(),
        )
    }


}
