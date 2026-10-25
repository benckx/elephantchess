package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.model.UserActivitySummaryRecord
import io.elephantchess.db.services.ArchivedPageViewDaoService
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.PuzzleResultDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsData
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.services.GameDataService.Companion.MIN_MOVE_INDEX
import io.elephantchess.servicelayer.services.MailService
import io.elephantchess.utils.di.KoinSingleton

@KoinSingleton
class AdminUserInsightsService(
    private val userDaoService: UserDaoService,
    private val puzzleResultDaoService: PuzzleResultDaoService,
    private val playerVsPlayerGameDaoService: PlayerVsPlayerGameDaoService,
    private val playerVsBotGameDaoService: PlayerVsBotGameDaoService,
    private val archivedPageViewDaoService: ArchivedPageViewDaoService,
    private val userSessionDaoService: UserSessionDaoService,
    private val mailService: MailService,
) {

    suspend fun fetchUserInsights(identifier: String): AdminUserInsightsData {
        val user = userDaoService.findByUserName(identifier)
            ?: userDaoService.findById(identifier)
            ?: throw NotFoundException("User $identifier could not be found")

        val puzzle = puzzleResultDaoService.fetchUserActivitySummary(user.id)
        val pvp = playerVsPlayerGameDaoService.fetchUserActivitySummary(user.id)
        val pvpOver3 = playerVsPlayerGameDaoService.fetchUserActivitySummary(user.id, MIN_MOVE_INDEX)
        val pvb = playerVsBotGameDaoService.fetchUserActivitySummary(user.id)
        val pvbOver3 = playerVsBotGameDaoService.fetchUserActivitySummary(user.id, MIN_MOVE_INDEX)
        val emailValidityStatus = user.email?.let { email -> mailService.getEmailValidityDetails(email) }
        val latestPageViews = archivedPageViewDaoService.listPageViewsForUser(user.id, limit = 20, offset = 0)
        val latestSessions = userSessionDaoService.listAuthenticatedSessionsForUser(user.id, limit = 5, offset = 0)

        return AdminUserInsightsData(
            userId = user.id,
            username = user.handle ?: user.id,
            email = user.email,
            userType = user.userType,
            country = user.country,
            creationTime = user.creation?.toEpochMilliseconds(),
            lastOnlineTime = user.lastOnline?.toEpochMilliseconds(),
            lastProfileUpdateTime = user.lastProfileUpdate?.toEpochMilliseconds(),
            emailConfirmedAtTime = user.emailConfirmedAt?.toEpochMilliseconds(),
            emailValidityStatus = emailValidityStatus,
            hasRoleAdmin = user.hasRoleAdmin ?: false,
            hasRoleEditor = user.hasRoleEditor ?: false,
            puzzle = puzzle.toDto(),
            pvp = pvp.toDto(),
            pvpOver3 = pvpOver3.toDto(),
            pvb = pvb.toDto(),
            pvbOver3 = pvbOver3.toDto(),
            latestPageViews = latestPageViews.map { record ->
                AdminUserInsightsData.PageViewEntry(
                    url = record.url,
                    time = record.eventTime.toEpochMilliseconds(),
                )
            },
            latestSessions = latestSessions.map { record ->
                AdminUserInsightsData.SessionEntry(
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

    private fun UserActivitySummaryRecord.toDto(): AdminUserInsightsData.ActivitySummary =
        AdminUserInsightsData.ActivitySummary(
            firstPlayed = firstPlayed?.toEpochMilliseconds(),
            lastPlayed = lastPlayed?.toEpochMilliseconds(),
            totalPlayed = totalPlayed,
        )
}
