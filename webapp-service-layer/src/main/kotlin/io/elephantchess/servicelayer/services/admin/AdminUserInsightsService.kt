package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.model.UserActivitySummaryRecord
import io.elephantchess.db.services.ArchivedPageViewDaoService
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.PuzzleResultDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsResponse
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.utils.di.KoinSingleton

@KoinSingleton
class AdminUserInsightsService(
    private val userDaoService: UserDaoService,
    private val puzzleResultDaoService: PuzzleResultDaoService,
    private val playerVsPlayerGameDaoService: PlayerVsPlayerGameDaoService,
    private val playerVsBotGameDaoService: PlayerVsBotGameDaoService,
    private val archivedPageViewDaoService: ArchivedPageViewDaoService,
    private val userSessionDaoService: UserSessionDaoService,
) {

    suspend fun fetchUserInsights(username: String): AdminUserInsightsResponse {
        val user = userDaoService.findByUserName(username)
            ?: throw NotFoundException("User $username could not be found")

        val puzzle = puzzleResultDaoService.fetchUserActivitySummary(user.id)
        val pvp = playerVsPlayerGameDaoService.fetchUserActivitySummary(user.id)
        val pvb = playerVsBotGameDaoService.fetchUserActivitySummary(user.id)
        val latestPageViews = archivedPageViewDaoService.listPageViewsForUser(user.id, limit = 10, offset = 0)
        val latestSessions = userSessionDaoService.listAuthenticatedSessionsForUser(user.id, limit = 3, offset = 0)

        return AdminUserInsightsResponse(
            userId = user.id,
            username = user.handle,
            email = user.email,
            userType = user.userType,
            country = user.country,
            creationTime = user.creation?.toEpochMilliseconds(),
            lastOnlineTime = user.lastOnline?.toEpochMilliseconds(),
            lastProfileUpdateTime = user.lastProfileUpdate?.toEpochMilliseconds(),
            emailConfirmedAtTime = user.emailConfirmedAt?.toEpochMilliseconds(),
            hasRoleAdmin = user.hasRoleAdmin,
            hasRoleEditor = user.hasRoleEditor,
            puzzle = puzzle.toDto(),
            pvp = pvp.toDto(),
            pvb = pvb.toDto(),
            latestPageViews = latestPageViews.map { record ->
                AdminUserInsightsResponse.PageViewEntry(
                    url = record.url,
                    time = record.eventTime.toEpochMilliseconds(),
                )
            },
            latestSessions = latestSessions.map { record ->
                AdminUserInsightsResponse.SessionEntry(
                    os = record.operatingSystemName,
                    agentName = record.agentName,
                    countryCode = record.countryCode,
                    countryName = record.countryName,
                    region = record.region,
                    city = record.city,
                    remoteAddress = record.remoteAddress,
                    created = record.created?.toEpochMilliseconds(),
                    updated = record.lastUpdated?.toEpochMilliseconds(),
                )
            },
        )
    }

    private fun UserActivitySummaryRecord.toDto(): AdminUserInsightsResponse.ActivitySummary =
        AdminUserInsightsResponse.ActivitySummary(
            firstPlayed = firstPlayed?.toEpochMilliseconds(),
            lastPlayed = lastPlayed?.toEpochMilliseconds(),
            totalPlayed = totalPlayed,
        )
}
