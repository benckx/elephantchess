package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.model.UserActivitySummaryRecord
import io.elephantchess.db.services.ArchivedPageViewDaoService
import io.elephantchess.db.services.ChatMessageDaoService
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.PuzzleResultDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.services.UserSessionDaoService
import io.elephantchess.servicelayer.dto.AutocompleteResponse
import io.elephantchess.servicelayer.dto.admin.AdminUserChatMessagesResponse
import io.elephantchess.servicelayer.dto.admin.AdminUserInsightsData
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
    private val playerVsBotGameDaoService: PlayerVsBotGameDaoService,
    private val chatMessageDaoService: ChatMessageDaoService,
    private val archivedPageViewDaoService: ArchivedPageViewDaoService,
    private val userSessionDaoService: UserSessionDaoService,
    private val analysisService: AnalysisService,
    private val mailService: MailService,
) {

    suspend fun fetchUserInsights(identifier: String): AdminUserInsightsData {
        val user = resolveUser(identifier)

        val puzzle = puzzleResultDaoService.fetchUserActivitySummary(user.id)
        val pvp = playerVsPlayerGameDaoService.fetchUserActivitySummary(user.id)
        val pvpOver3 = playerVsPlayerGameDaoService.fetchUserActivitySummary(user.id, MIN_MOVE_INDEX)
        val pvb = playerVsBotGameDaoService.fetchUserActivitySummary(user.id)
        val pvbOver3 = playerVsBotGameDaoService.fetchUserActivitySummary(user.id, MIN_MOVE_INDEX)
        val emailValidityStatus = user.email?.let { email -> mailService.getEmailValidityDetails(email) }
        val latestChatMessages = chatMessageDaoService.listLastMessagesByAuthor(user.id, limit = 10)
        val latestPageViews = archivedPageViewDaoService.listPageViewsForUser(user.id, limit = 10, offset = 0)
        val latestSessions = userSessionDaoService.listAuthenticatedSessionsForUser(user.id, limit = 5, offset = 0)
        val relatedUsers = userSessionDaoService.listOtherUsersByRemoteAddresses(
            userId = user.id,
            remoteAddresses = latestSessions.map { it.remoteAddress }.toSet(),
            limit = 20,
        )
        val pvpGamesWithRelatedUser = relatedUsers.associate { relatedUser ->
            relatedUser.userId to playerVsPlayerGameDaoService.countGamesBetweenUsers(user.id, relatedUser.userId)
        }
        val savedAnalysis = analysisService.listUserAnalysis(user.id, beforeTs = null).entries.take(10)
        val otherUsersByRemoteAddress = userSessionDaoService.countOtherUsersByRemoteAddresses(
            userId = user.id,
            remoteAddresses = latestSessions.map { it.remoteAddress }.toSet(),
        )

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
            relatedUsers = relatedUsers.map { relatedUser ->
                AdminUserInsightsData.RelatedUserEntry(
                    userId = relatedUser.userId,
                    username = userDaoService.findUserNameById(relatedUser.userId) ?: relatedUser.userId,
                    sharedIpCount = relatedUser.sharedRemoteAddressCount,
                    pvpGamesWithUser = pvpGamesWithRelatedUser[relatedUser.userId] ?: 0,
                    lastSeen = relatedUser.lastSeen?.toEpochMilliseconds(),
                )
            },
            savedAnalysis = savedAnalysis.map { analysis ->
                AdminUserInsightsData.SavedAnalysisEntry(
                    analysisId = analysis.analysisId,
                    name = analysis.name,
                    gameType = analysis.gameType,
                    created = analysis.created,
                    lastUpdated = analysis.lastUpdated,
                )
            },
            latestChatMessages = latestChatMessages.map { record ->
                AdminUserInsightsData.ChatMessageEntry(
                    gameId = record.gameId,
                    messageTime = record.messageTime.toEpochMilliseconds(),
                    content = record.content,
                )
            },
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
                    otherUsersOnRemoteAddress = otherUsersByRemoteAddress[record.remoteAddress] ?: 0,
                    created = record.created?.toEpochMilliseconds(),
                    updated = record.lastUpdated?.toEpochMilliseconds(),
                )
            },
        )
    }

    suspend fun listUserSuggestions(contains: String): AutocompleteResponse {
        val entries = userDaoService.listUsersStartingWith(contains, SUGGESTIONS_LIMIT)
            .ifEmpty { userDaoService.listUsersContaining(contains, SUGGESTIONS_LIMIT) }

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

    private fun UserActivitySummaryRecord.toDto(): AdminUserInsightsData.ActivitySummary =
        AdminUserInsightsData.ActivitySummary(
            firstPlayed = firstPlayed?.toEpochMilliseconds(),
            lastPlayed = lastPlayed?.toEpochMilliseconds(),
            totalPlayed = totalPlayed,
        )

    private companion object {
        const val SUGGESTIONS_LIMIT = 50
    }
}
