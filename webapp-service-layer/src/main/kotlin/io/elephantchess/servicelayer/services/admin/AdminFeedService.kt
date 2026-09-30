package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.dao.codegen.Tables.PUZZLE_RESULT
import io.elephantchess.db.dao.codegen.Tables.USER
import io.elephantchess.db.dao.codegen.tables.pojos.BotGame
import io.elephantchess.db.dao.codegen.tables.pojos.Game
import io.elephantchess.db.services.AnalysisDaoService
import io.elephantchess.db.services.ContentSectionVoteDaoService
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.PuzzleResultDaoService
import io.elephantchess.db.utils.winnerUserId
import io.elephantchess.model.GameEventType.AUTO_CANCELED
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.admin.ListBotGamesResponse
import io.elephantchess.servicelayer.dto.admin.ListContentSectionVoteFeedbackResponse
import io.elephantchess.servicelayer.dto.admin.ListGamesResponse
import io.elephantchess.servicelayer.dto.admin.ListLastPuzzleByLoggedInUsersResponse
import io.elephantchess.servicelayer.dto.admin.ListLastUserAnalysisResponse
import io.elephantchess.servicelayer.services.UserCache
import io.elephantchess.xiangqi.Variant
import io.elephantchess.xiangqi.Variant.XIANGQI

class AdminFeedService(
    private val analysisDaoService: AnalysisDaoService,
    private val pvbGameDaoService: PlayerVsBotGameDaoService,
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    private val puzzleResultDaoService: PuzzleResultDaoService,
    private val contentSectionVoteDaoService: ContentSectionVoteDaoService,
    private val userCache: UserCache,
) {
    suspend fun listLastPuzzlePlayedByLoggedUsers(): ListLastPuzzleByLoggedInUsersResponse {
        return puzzleResultDaoService
            .listLastPuzzlePlayedByLoggedUsers(24)
            .map { record ->
                val userId = record.get(USER.ID)
                val username = userCache.fetchUsername(userId)
                val userType = userCache.fetchUserType(userId)

                ListLastPuzzleByLoggedInUsersResponse.Entry(
                    puzzleId = record.get(PUZZLE_RESULT.PUZZLE_ID),
                    outcome = record.get(PUZZLE_RESULT.OUTCOME),
                    userId = userId,
                    userType = userType,
                    username = username,
                    date = record.get(PUZZLE_RESULT.ENTRY_CREATION).toEpochMilliseconds(),
                    upVoted = record.get(PUZZLE_RESULT.UP_VOTED)
                )
            }
            .let { entries ->
                ListLastPuzzleByLoggedInUsersResponse(entries)
            }
    }

    /**
     * List the most recent analysis, with their users
     */
    suspend fun listLastUsersAnalysis(): ListLastUserAnalysisResponse {
        // TODO: show the number of nodes
        val entries =
            analysisDaoService
                .listAnalysisForAllUsers(15)
                .map { record ->
                    ListLastUserAnalysisResponse.Entry(
                        analysisId = record.analysisId(),
                        currentVersion = record.versionNumber(),
                        name = record.analysisName(),
                        created = record.created().toEpochMilliseconds(),
                        lastUpdated = record.lastUpdated().toEpochMilliseconds(),
                        userId = record.userId(),
                        username = record.username()
                    )
                }

        return ListLastUserAnalysisResponse(entries)
    }

    suspend fun listLatestPvpGames(): ListGamesResponse {
        return pvpGameDaoService
            .listLastGames(
                limit = 60,
                statusToExcludes = listOf(AUTO_CANCELED),
                variantsToInclude = listOf(XIANGQI)
            )
            .map { record -> mapGameToDto(record) }
            .let { entries -> ListGamesResponse(entries) }
    }

    suspend fun listLatestVariantPvpGames(): ListGamesResponse {
        return pvpGameDaoService
            .listLastGames(
                60,
                statusToExcludes = listOf(AUTO_CANCELED),
                variantsToInclude = nonXiangqiVariants
            )
            .map { record -> mapGameToDto(record) }
            .let { entries -> ListGamesResponse(entries) }
    }

    suspend fun listLatestPvbGames(): ListBotGamesResponse {
        return pvbGameDaoService
            .listLatestGamesByIdentifiedUsers(
                limit = 80,
                variantsToInclude = listOf(XIANGQI)
            )
            .map { record -> mapBotGameToDto(record) }
            .let { entries -> ListBotGamesResponse(entries) }
    }

    suspend fun listLatestVariantPvbGames(): ListBotGamesResponse {
        return pvbGameDaoService
            .listLatestGamesByIdentifiedUsers(
                limit = 80,
                variantsToInclude = nonXiangqiVariants
            )
            .map { record -> mapBotGameToDto(record) }
            .let { entries -> ListBotGamesResponse(entries) }
    }

    suspend fun listLatestFeedback(): ListContentSectionVoteFeedbackResponse {
        val entries = contentSectionVoteDaoService
            .listLatestFeedback(250)
            .map { record ->
                ListContentSectionVoteFeedbackResponse.Entry(
                    userId = record.userId,
                    username = userCache.fetchUsernameOrDefault(record.userId),
                    userType = userCache.fetchUserType(record.userId) ?: UserType.GUEST,
                    pageId = record.pageId,
                    sectionId = record.sectionId,
                    upVoted = record.upVoted,
                    feedback = record.feedback ?: "",
                    creationTime = record.creationTime.toEpochMilliseconds(),
                    updateTime = record.updateTime.toEpochMilliseconds(),
                )
            }
        return ListContentSectionVoteFeedbackResponse(entries)
    }

    private suspend fun mapGameToDto(record: Game): ListGamesResponse.Entry {
        return ListGamesResponse.Entry(
            gameId = record.id,
            inviterUserId = record.inviter,
            inviterUsername = userCache.fetchUsernameOrDefault(record.inviter!!),
            inviteeUserId = record.invitee,
            inviteeUsername = record.invitee?.let { userCache.fetchUsernameOrDefault(it) },
            isRated = record.isRated,
            allowGuests = record.allowGuestsToJoin,
            alwaysVisibleInLobby = record.alwaysVisibleInLobby,
            privateInvite = record.privateInvite,
            timeControlBase = record.timeControlBase,
            timeControlIncrement = record.timeControlIncrement,
            status = record.gameStatus,
            index = record.currentHalfMoveIndex,
            winnerUserId = record.winnerUserId(),
            created = record.created.toEpochMilliseconds(),
            lastUpdated = record.lastUpdated.toEpochMilliseconds(),
            sourceType = record.joinSource,
            variant = record.variant
        )
    }

    private suspend fun mapBotGameToDto(record: BotGame): ListBotGamesResponse.Entry {
        return ListBotGamesResponse.Entry(
            gameId = record.id,
            userId = record.userId,
            username = record.userId?.let { userCache.fetchUsernameOrDefault(it) },
            userType = record.userId?.let { userCache.fetchUserType(it) },
            color = record.userColor,
            engine = record.engine,
            depth = record.depth,
            customStartFen = record.startFen != null,
            status = record.gameStatus,
            outcome = record.outcome,
            index = record.currentHalfMoveIndex,
            created = record.created.toEpochMilliseconds(),
            lastUpdated = record.lastUpdated.toEpochMilliseconds(),
            variant = record.variant,
        )
    }

    private companion object {

        val nonXiangqiVariants = Variant.entries.filter { it != XIANGQI }

    }

}
