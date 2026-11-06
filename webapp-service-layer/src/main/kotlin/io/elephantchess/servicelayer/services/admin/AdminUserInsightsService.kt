package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.model.UserActivitySummaryRecord
import io.elephantchess.db.services.ArchivedPageViewDaoService
import io.elephantchess.db.services.ChatMessageDaoService
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.PuzzleResultDaoService
import io.elephantchess.db.services.SockPuppetReportDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.servicelayer.dto.AutocompleteResponse
import io.elephantchess.servicelayer.dto.admin.AdminUserChatMessagesResponse
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsDetailsResponse
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsGameAndSavedAnalysisResponse
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsLatestChatMessagesResponse
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsLatestPageViewsResponse
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsLatestSessionsResponse
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsMetadata
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsRelatedUsersAndSockPuppetGamesResponse
import io.elephantchess.servicelayer.dto.admin.AdminUserPageViewsResponse
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.services.AnalysisService
import io.elephantchess.servicelayer.services.GameDataService.Companion.MIN_MOVE_INDEX
import io.elephantchess.servicelayer.services.MailService
import io.elephantchess.utils.di.KoinSingleton

@KoinSingleton
class AdminUserInsightsService(
    private val userDaoService: UserDaoService,
    private val puzzleResultDaoService: PuzzleResultDaoService,
    private val playerVsPlayerGameDaoService: PlayerVsPlayerGameDaoService,
    private val sockPuppetReportDaoService: SockPuppetReportDaoService,
    private val playerVsBotGameDaoService: PlayerVsBotGameDaoService,
    private val chatMessageDaoService: ChatMessageDaoService,
    private val archivedPageViewDaoService: ArchivedPageViewDaoService,
    private val userSessionDaoService: UserSessionDaoService,
    private val analysisService: AnalysisService,
    private val mailService: MailService,
) {

    suspend fun fetchUserMetadata(identifier: String): AdminUserInsightsMetadata {
        val user = resolveUser(identifier)
        val emailValidityStatus = user.email?.let { email -> mailService.getEmailValidityDetails(email) }

        return AdminUserInsightsMetadata(
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
        )
    }

    suspend fun fetchUserInsightsGameAndSavedAnalysis(identifier: String): AdminUserInsightsGameAndSavedAnalysisResponse {
        val user = resolveUser(identifier)

        val puzzle = puzzleResultDaoService.fetchUserActivitySummary(user.id)
        val pvp = playerVsPlayerGameDaoService.fetchUserActivitySummary(user.id)
        val pvpOver3 = playerVsPlayerGameDaoService.fetchUserActivitySummary(user.id, MIN_MOVE_INDEX)
        val pvb = playerVsBotGameDaoService.fetchUserActivitySummary(user.id)
        val pvbOver3 = playerVsBotGameDaoService.fetchUserActivitySummary(user.id, MIN_MOVE_INDEX)
        val savedAnalysis = analysisService.listUserAnalysis(user.id, beforeTs = null).entries.take(10)

        return AdminUserInsightsGameAndSavedAnalysisResponse(
            puzzle = puzzle.toDto(),
            pvp = pvp.toDto(),
            pvpOver3 = pvpOver3.toDto(),
            pvb = pvb.toDto(),
            pvbOver3 = pvbOver3.toDto(),
            savedAnalysis = savedAnalysis.map { analysis ->
                AdminUserInsightsDetailsResponse.SavedAnalysisEntry(
                    analysisId = analysis.analysisId,
                    name = analysis.name,
                    gameType = analysis.gameType,
                    created = analysis.created,
                    lastUpdated = analysis.lastUpdated,
                )
            },
        )
    }

    suspend fun fetchUserInsightsRelatedUsersAndSockPuppetGames(
        identifier: String,
    ): AdminUserInsightsRelatedUsersAndSockPuppetGamesResponse {
        val user = resolveUser(identifier)
        val latestSessions = userSessionDaoService.listAuthenticatedSessionsForUser(user.id, limit = 5, offset = 0)
        val relatedUsers = userSessionDaoService.listOtherUsersByRemoteAddresses(
            userId = user.id,
            remoteAddresses = latestSessions.map { it.remoteAddress }.toSet(),
            limit = 20,
        )
        val sockPuppetGames = sockPuppetReportDaoService.listDetectionsForUser(user.id)
        val pvpGamesWithRelatedUser = relatedUsers.associate { relatedUser ->
            relatedUser.userId to playerVsPlayerGameDaoService.countGamesBetweenUsers(user.id, relatedUser.userId)
        }

        return AdminUserInsightsRelatedUsersAndSockPuppetGamesResponse(
            relatedUsers = relatedUsers.map { relatedUser ->
                AdminUserInsightsDetailsResponse.RelatedUserEntry(
                    userId = relatedUser.userId,
                    username = userDaoService.findUserNameById(relatedUser.userId) ?: relatedUser.userId,
                    sharedIpCount = relatedUser.sharedRemoteAddressCount,
                    pvpGamesWithUser = pvpGamesWithRelatedUser[relatedUser.userId] ?: 0,
                    lastSeen = relatedUser.lastSeen?.toEpochMilliseconds(),
                )
            },
            sockPuppetGames = sockPuppetGames.map { report ->
                AdminUserInsightsDetailsResponse.SockPuppetGameEntry(
                    gameId = report.gameId,
                    inviterUserId = report.inviterUserId,
                    inviterUsername = userDaoService.findUserNameById(report.inviterUserId) ?: report.inviterUserId,
                    inviteeUserId = report.inviteeUserId,
                    inviteeUsername = userDaoService.findUserNameById(report.inviteeUserId) ?: report.inviteeUserId,
                    sharedRemoteAddressCount = report.sharedRemoteAddressCount,
                    sharedFingerprintCount = report.sharedFingerprintCount,
                    sharedRemoteAddress = report.sharedRemoteAddress,
                    sharedCountryName = report.sharedCountryName,
                    sharedRegion = report.sharedRegion,
                    sharedCity = report.sharedCity,
                    sharedOperatingSystemName = report.sharedOperatingSystemName,
                    sharedAgentName = report.sharedAgentName,
                    gameCreated = report.gameCreated.toEpochMilliseconds(),
                )
            },
        )
    }

    suspend fun fetchUserInsightsLatestChatMessages(identifier: String): AdminUserInsightsLatestChatMessagesResponse {
        val user = resolveUser(identifier)
        val latestChatMessages = chatMessageDaoService.listMessagesByAuthor(user.id, limit = 10, offset = null)

        return AdminUserInsightsLatestChatMessagesResponse(
            latestChatMessages = latestChatMessages.map { record ->
                AdminUserInsightsDetailsResponse.ChatMessageEntry(
                    gameId = record.gameId,
                    messageTime = record.messageTime.toEpochMilliseconds(),
                    content = record.content,
                )
            },
        )
    }

    suspend fun fetchUserInsightsLatestPageViews(identifier: String): AdminUserInsightsLatestPageViewsResponse {
        val user = resolveUser(identifier)
        val latestPageViews = archivedPageViewDaoService.listPageViewsForUser(user.id, limit = 10, offset = 0)

        return AdminUserInsightsLatestPageViewsResponse(
            latestPageViews = latestPageViews.map { record ->
                AdminUserInsightsDetailsResponse.PageViewEntry(
                    url = record.url,
                    time = record.eventTime.toEpochMilliseconds(),
                )
            },
        )
    }

    suspend fun fetchUserInsightsLatestSessions(identifier: String): AdminUserInsightsLatestSessionsResponse {
        val user = resolveUser(identifier)
        val latestSessions = userSessionDaoService.listAuthenticatedSessionsForUser(user.id, limit = 5, offset = 0)
        val otherUsersByRemoteAddress = userSessionDaoService.countOtherUsersByRemoteAddresses(
            userId = user.id,
            remoteAddresses = latestSessions.map { it.remoteAddress }.toSet(),
        )

        return AdminUserInsightsLatestSessionsResponse(
            latestSessions = latestSessions.map { record ->
                AdminUserInsightsDetailsResponse.SessionEntry(
                    os = record.operatingSystemName,
                    agentName = record.agentName,
                    countryCode = record.countryCode,
                    countryName = record.countryName,
                    region = record.region,
                    city = record.city,
                    remoteAddress = record.remoteAddress,
                    otherUsersOnRemoteAddress = otherUsersByRemoteAddress[record.remoteAddress] ?: 0,
                    created = record.created?.toEpochMilliseconds(),
                    updated = record.lastUpdated?.toEpochMilliseconds(),
                )
            },
        )
    }

    suspend fun listUserSuggestions(contains: String): AutocompleteResponse {
        val entries = userDaoService.listUsersStartingWithIdentifier(contains, SUGGESTIONS_LIMIT)
            .ifEmpty { userDaoService.listUsersContainingIdentifier(contains, SUGGESTIONS_LIMIT) }

        return AutocompleteResponse(
            entries = entries.map { entry ->
                AutocompleteResponse.Entry(
                    id = entry.id,
                    name = entry.name,
                )
            },
        )
    }

    suspend fun listUserPageViews(identifier: String, limit: Int, offset: Int): AdminUserPageViewsResponse {
        val user = resolveUser(identifier)
        val entries = archivedPageViewDaoService.listPageViewsForUser(user.id, limit, offset)
            .map { record ->
                AdminUserPageViewsResponse.Entry(
                    url = record.url,
                    time = record.eventTime.toEpochMilliseconds(),
                )
            }

        return AdminUserPageViewsResponse(entries)
    }

    suspend fun listUserChatMessages(identifier: String, limit: Int, offset: Int): AdminUserChatMessagesResponse {
        val user = resolveUser(identifier)
        val entries = chatMessageDaoService.listMessagesByAuthor(user.id, limit, offset)
            .map { record ->
                AdminUserChatMessagesResponse.Entry(
                    gameId = record.gameId,
                    messageTime = record.messageTime.toEpochMilliseconds(),
                    content = record.content,
                )
            }

        return AdminUserChatMessagesResponse(entries)
    }

    private suspend fun resolveUser(identifier: String) =
        userDaoService.findByUserName(identifier)
            ?: userDaoService.findById(identifier)
            ?: throw NotFoundException("User $identifier could not be found")

    private fun UserActivitySummaryRecord.toDto(): AdminUserInsightsDetailsResponse.ActivitySummary =
        AdminUserInsightsDetailsResponse.ActivitySummary(
            firstPlayed = firstPlayed?.toEpochMilliseconds(),
            lastPlayed = lastPlayed?.toEpochMilliseconds(),
            totalPlayed = totalPlayed,
        )

    private companion object {
        const val SUGGESTIONS_LIMIT = 50
    }
}
