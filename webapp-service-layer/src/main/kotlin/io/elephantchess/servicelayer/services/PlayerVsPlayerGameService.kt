package io.elephantchess.servicelayer.services

import io.elephantchess.db.callback.PerpetualCheckingCallbackResult
import io.elephantchess.db.callback.PlayMoveCallbackResult
import io.elephantchess.db.callback.UpdateRatingsCallbackResult
import io.elephantchess.db.dao.codegen.tables.pojos.Game
import io.elephantchess.db.dao.codegen.tables.pojos.GameChatMessage
import io.elephantchess.db.model.TimeControlRecord
import io.elephantchess.db.services.ChatMessageDaoService
import io.elephantchess.db.services.PlayerVsPlayerGameDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.*
import io.elephantchess.model.*
import io.elephantchess.model.GameEventType.*
import io.elephantchess.model.GameEventType.Companion.gameEndedStatuses
import io.elephantchess.model.Outcome.BLACK_WINS
import io.elephantchess.model.Outcome.RED_WINS
import io.elephantchess.servicelayer.dto.ChatMessage
import io.elephantchess.servicelayer.dto.game.*
import io.elephantchess.servicelayer.dto.game.RatingUpdate
import io.elephantchess.servicelayer.dto.gamedata.GameMovesResponse
import io.elephantchess.servicelayer.dto.ws.*
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.exceptions.ForbiddenException
import io.elephantchess.servicelayer.exceptions.InternalErrorException
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.ws.GamesToPlayWebSocketSession
import io.elephantchess.servicelayer.services.ws.PlayerVsPlayerWebSocketSession
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRate
import io.elephantchess.servicelayer.utils.ops.ratingUpdate
import io.elephantchess.utils.EloCalculator.calculateElo
import io.elephantchess.utils.TryEither
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.calculateNewFen
import io.elephantchess.xiangqi.Board.Companion.isCheckmated
import io.elephantchess.xiangqi.Board.Companion.isInCheck
import io.elephantchess.xiangqi.Board.Companion.isMoveLegal
import io.elephantchess.xiangqi.Board.Companion.isStalemated
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.PerpetualCheckingRule.Companion.defaultPerpetualCheckingRules
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ChannelResult
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import io.elephantchess.servicelayer.dto.ws.RatingUpdate as RatingUpdateWs

class PlayerVsPlayerGameService(
    private val userService: UserService,
    private val userDaoService: UserDaoService,
    private val mailService: MailService,
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    private val chatMessageDaoService: ChatMessageDaoService,
    private val userCache: UserCache,
    private val discordService: DiscordService,
    private val logger: KLogger,
    refresherScope: CoroutineScope
) {

    private val sessionsRefresh = 2.seconds

    private val perpetualCheckRules by lazy { defaultPerpetualCheckingRules }
    private val gamesToPlaySessions = mutableListOf<GamesToPlayWebSocketSession>()
    private val playerVsPlayerSessions = mutableListOf<PlayerVsPlayerWebSocketSession>()

    private val refreshJob = launchAtFixedRate(
        scope = refresherScope,
        initialDelay = sessionsRefresh,
        period = sessionsRefresh,
        action = {
            refreshGamesToPlaySessions()
            refreshPlayerVsPlayerSessions()
        }
    )

    fun cancel() {
        refreshJob.cancel()
    }

    private suspend fun refreshGamesToPlaySessions() {
        val onlineUserIds = userService.onlineUserIds()

        // refresh games to play sessions
        if (gamesToPlaySessions.isNotEmpty() && onlineUserIds.isNotEmpty()) {
            val allGamesToJoin = fetchAllGamesOpenToJoin(onlineUserIds)
            val allActiveGames = fetchAllActiveGames(onlineUserIds)

            gamesToPlaySessions.forEach { session ->
                val gamesCanJoin = when (session.userType) {
                    UserType.GUEST -> allGamesToJoin.filter { it.allowGuestsToJoin }
                    UserType.AUTHENTICATED -> allGamesToJoin
                }

                session.update(
                    GamesToPlayUpdate(
                        gamesToJoin = gamesCanJoin
                            .map { game ->
                                mapToGameToPlayDto(
                                    game = game,
                                    userId = game.inviter,
                                    onlineUserIds = onlineUserIds
                                )
                            },
                        turnToPlayGames = allActiveGames
                            .filter { game -> game.includesUser(session.userId) }
                            .filter { game -> game.isTurnToPlay(session.userId) }
                            .map { game ->
                                mapToGameToPlayDto(
                                    game = game,
                                    userId = game.opponentOf(session.userId)!!,
                                    onlineUserIds = onlineUserIds
                                )
                            },
                        totalOnline = onlineUserIds.size
                    )
                )
            }
        }

        // remove the sessions that are not active anymore
        gamesToPlaySessions.removeIf { session ->
            if (session.isClosed) logger.debug { "removing $session" }
            session.isClosed
        }

        userDaoService.updateLastOnline(gamesToPlaySessions.map { it.userId })
    }

    private suspend fun refreshPlayerVsPlayerSessions() {
        val allGameIds = playerVsPlayerSessions.map { session -> session.gameId }.distinct()
        val stateMap = pvpGameDaoService.fetchGameStates(allGameIds)
        val chatIndexes = chatMessageDaoService.currentIndexes(allGameIds)

        // not very optimized: 2 sessions about the same game -> some information will be fetched 2x from the db
        playerVsPlayerSessions
            .forEach { session ->
                val gameId = session.gameId
                val gameState = stateMap[gameId]!!
                val status = gameState.gameEventType
                val index = gameState.index

                // update opponent if necessary
                var hasJoinedEvent: HasJoined? = null
                if (session.isWaitingToBeJoined()) {
                    val hasJoinedRecord = pvpGameDaoService.fetchHasJoinedData(gameId)
                    if (hasJoinedRecord?.invitee != null) {
                        val inviteeId = hasJoinedRecord.invitee!!
                        hasJoinedEvent = HasJoined(
                            inviteeId = inviteeId,
                            inviteeUsername = userCache.fetchUsernameOrDefault(inviteeId),
                            inviteeRating = hasJoinedRecord.inviteeRating!!,
                            inviteeUserType = userCache.fetchUserType(inviteeId)!!,
                            inviterColor = hasJoinedRecord.inviterColor
                        )
                    }
                }

                // update new move if necessary
                var newMove: NewMove? = null
                if (session.currentIndex() < index) {
                    newMove = NewMove(
                        move = pvpGameDaoService.fetchMoveAt(gameId, index - 1)!!.uci,
                        updatedIndex = index,
                        updatedFen = gameState.fen,
                    )
                }

                // time remaining
                var timeRemaining: TimeRemaining? = null
                if (session.mustSyncTime(Clock.System.now())) {
                    timeRemaining = fetchTimeRemaining(gameId)
                }

                val chatMessages = mutableListOf<ChatMessage>()
                if (session.currentChatIndex() < chatIndexes[gameId]!!) {
                    chatMessages.addAll(
                        chatMessageDaoService
                            .listMessagesAfterOrEqualToIndex(gameId, session.currentChatIndex())
                            .map { record -> mapRecordToDto(record) }
                    )
                }

                val shouldUpdate =
                    session.currentStatus() != status ||
                            newMove != null ||
                            hasJoinedEvent != null ||
                            timeRemaining != null ||
                            chatMessages.isNotEmpty()

                // update session
                if (shouldUpdate) {
                    var drawPropositionUser: String? = null
                    if (status == DRAW_PROPOSED) {
                        drawPropositionUser = pvpGameDaoService.fetchDrawPropositionUser(gameId)
                    }

                    session.update(
                        PlayerVsPlayerUpdate(
                            status = status,
                            hasJoined = hasJoinedEvent,
                            drawPropositionUser = drawPropositionUser,
                            newMove = newMove,
                            ratingUpdate = fetchRatingUpdateIfNecessaryWs(session.gameId, status),
                            timeRemaining = timeRemaining,
                            chatMessages = chatMessages,
                        )
                    )
                }
            }

        // remove the sessions that are not active anymore
        playerVsPlayerSessions.removeIf { session ->
            if (session.isClosed) logger.debug { "removing $session" }
            session.isClosed
        }
    }

    private suspend fun fetchAllGamesOpenToJoin(onlineUserIds: Set<String>) =
        pvpGameDaoService
            .listGamesOpenToJoin()
            .filter { gameRecord ->
                gameRecord.alwaysVisibleInLobby ||
                        onlineUserIds.contains(gameRecord.inviter)
            }

    private suspend fun fetchAllActiveGames(onlineUserIds: Set<String>) =
        pvpGameDaoService
            .listAllActiveGames()
            .filter { gameRecord ->
                onlineUserIds.contains(gameRecord.inviter) ||
                        onlineUserIds.contains(gameRecord.invitee)
            }

    suspend fun createGame(userId: UserId, request: CreateGameRequest): CreateGameResponse {
        // validation
        if (request.timeControlMode == TimeControlMode.MOVE_TIME) {
            if (request.timeControlIncrement != null) {
                throw IllegalArgumentException("Increment is not allowed when time control is move time-based")
            }

            if (request.timeControlBase < 1.days.inWholeSeconds) {
                throw IllegalArgumentException("Correspondence games must be at least 1 day long")
            }
        }

        validateUserType(userId)

        // correspondence games can not be created by guest
        if (userId.userType == UserType.GUEST && request.timeControlMode == TimeControlMode.MOVE_TIME) {
            throw BadRequestException("Guest users are not allowed to create correspondence games")
        }

        // limit the number of CREATED games a user can have with the same settings
        val createdGamesCount = pvpGameDaoService.countCreatedGamesByUser(
            userId.id,
            request.inviterColor,
            request.timeControlMode,
            request.timeControlBase,
            request.timeControlIncrement,
        )
        if (createdGamesCount >= MAX_CREATED_GAMES_PER_SETTINGS) {
            throw BadRequestException("You already have $MAX_CREATED_GAMES_PER_SETTINGS pending games with the same settings")
        }

        // if user is a guest, always allow guests to join
        // it should be disabled by UI already, so it's additional backend validation
        val allowGuests = userId.userType == UserType.GUEST || request.allowGuests

        val timeControlCategory = TimeControlCategory.fromSeconds(request.timeControlBase)
        val userRating = getUserRating(userId.id, timeControlCategory)

        // if such a game already exists, join it instead of creating a new one
        if (!request.privateInvite) {
            findMatchingGame(userId, userRating, request)
                ?.let { game ->
                    val joinGameRequest = JoinGameRequest(game.id, GameJoinSource.MATCHED, null)
                    val response = joinGame(userId, joinGameRequest)
                    logger.debug { "user $userId was matched with game ${game.id}" }
                    return CreateGameResponse(game.id, JOINED, response.inviteeColor)
                }
        }

        val game = Game()
        game.id = generateId()
        game.inviter = userId.id
        game.inviterColor = request.inviterColor
        game.currentFen = DEFAULT_START_FEN
        game.gameStatus = CREATED
        game.currentHalfMoveIndex = 0
        game.allowGuestsToJoin = allowGuests
        game.alwaysVisibleInLobby = !request.privateInvite && request.alwaysVisibleInLobby
        game.privateInvite = request.privateInvite
        game.containsErrors = false

        val now = Clock.System.now()
        game.created = now
        game.lastUpdated = now

        // time control
        game.timeControlMode = request.timeControlMode
        game.timeControlBase = request.timeControlBase
        game.timeControlIncrement = request.timeControlIncrement
        game.timeControlCategory = timeControlCategory

        // rating
        game.isRated = request.isRated
        if (request.isRated) {
            game.kValue = 16
        }
        game.inviterRatingFrom = userRating

        pvpGameDaoService.insertGame(userId.id, game)
        discordService.gameCreated(userId.id, game)
        return CreateGameResponse(game.id, CREATED, request.inviterColor)
    }

    private suspend fun findMatchingGame(
        userId: UserId,
        userRating: Int,
        request: CreateGameRequest,
    ): Game? {
        return pvpGameDaoService
            .listCompatibleGames(
                inviterColor = request.inviterColor,
                isRated = request.isRated,
                timeControlMode = request.timeControlMode,
                timeControlBase = request.timeControlBase,
                timeControlIncrement = request.timeControlIncrement,
                userType = userId.userType,
                userId = userId.id
            )
            .filter { gameRecord -> isOnline(gameRecord.inviter) }
            .minByOrNull { gameRecord -> abs(gameRecord.inviterRatingFrom - userRating) }
    }

    /**
     * List games userId has played or is playing.
     */
    suspend fun listUserGames(userId: String, beforeTs: Long?): ListUserGamesResponse {
        val records = pvpGameDaoService.listGameByUserId(userId, 60, beforeTs)
        val gameIds = records.map { it.id }
        val numberOfMessagesCount = chatMessageDaoService.countMessages(gameIds)

        return records
            .map { gameRecord ->
                val opponentUserId = gameRecord.opponentOf(userId)
                val opponentUsername = opponentUserId?.let { userCache.fetchUsernameOrDefault(it) }
                val opponentUserType = opponentUserId?.let { userCache.fetchUserType(it) }
                val numberOfMessages = numberOfMessagesCount[gameRecord.id] ?: 0

                val color = gameRecord.userColor(userId)
                val colorToPlay = gameRecord.colorToPlay()
                val userHasToPlay = color == colorToPlay && gameRecord.gameStatus.isInProgress()

                var ratingFrom: Int? = null
                var ratingTo: Int? = null

                if (gameRecord.inviter == userId) {
                    ratingFrom = gameRecord.inviterRatingFrom
                    ratingTo = gameRecord.inviterRatingTo
                } else if (gameRecord.invitee == userId) {
                    ratingFrom = gameRecord.inviteeRatingFrom
                    ratingTo = gameRecord.inviteeRatingTo
                }

                ListUserGamesResponse.Entry(
                    gameId = gameRecord.id,
                    status = gameRecord.gameStatus,
                    moveIndex = gameRecord.currentHalfMoveIndex,
                    currentFen = gameRecord.currentFen,
                    userHasToPlay = userHasToPlay,
                    color = color,
                    isRated = gameRecord.isRated,
                    timeControlCategory = gameRecord.timeControlCategory,
                    opponentUserType = opponentUserType,
                    opponentUserId = opponentUserId,
                    opponentUsername = opponentUsername,
                    outcome = gameRecord.userOutcome(userId),
                    ratingFrom = ratingFrom,
                    ratingTo = ratingTo,
                    created = gameRecord.created.toEpochMilliseconds(),
                    lastUpdated = gameRecord.lastUpdated.toEpochMilliseconds(),
                    numberOfMessages = numberOfMessages
                )
            }
            .let { entries ->
                ListUserGamesResponse(entries)
            }
    }

    suspend fun startGamesToPlaySession(
        userId: UserId,
        sendCb: (GamesToPlayUpdate) -> ChannelResult<Unit>,
    ): String {
        validateUserType(userId)

        val session = GamesToPlayWebSocketSession(userId, sendCb)
        gamesToPlaySessions.add(session)
        return session.sessionId
    }

    fun closeGamesToPlaySession(sessionId: String) {
        gamesToPlaySessions
            .find { it.sessionId == sessionId }
            ?.markAsClosed()
    }

    suspend fun startPlayerVsPlayerSession(
        userId: UserId,
        gameId: String,
        sendCb: (PlayerVsPlayerUpdate) -> ChannelResult<Unit>,
    ): String {
        validateUserType(userId)

        val gameState =
            pvpGameDaoService.fetchGameStates(listOf(gameId))[gameId]
                ?: throw NotFoundException("Game $gameId not found")

        val session =
            PlayerVsPlayerWebSocketSession(
                gameId = gameId,
                status = gameState.gameEventType,
                moveIndex = gameState.index,
                chatIndex = chatMessageDaoService.currentIndex(gameId),
                sendCb = sendCb
            )
        playerVsPlayerSessions += session
        logger.debug { "created $session for $userId" }

        return session.sessionId
    }

    suspend fun handlePlayerVsPlayerInput(userId: UserId, gameId: String, input: PlayerVsPlayerInput) {
        val gamePlayersStatus = fetchPlayersAndStatus(gameId)
        val isAllowedToChat = gamePlayersStatus.status == CREATED || gamePlayersStatus.isPlaying(userId.id)

        if (isAllowedToChat) {
            chatMessageDaoService.insertChat(
                gameId = gameId,
                userId = userId.id,
                content = input.message.trim().take(MESSAGE_LENGTH_LIMIT)
            )
        } else {
            logger.warn { "user $userId is not allowed to send message in game $gameId" }
        }
    }

    fun closePlayerVsPlayerSession(sessionId: String) {
        playerVsPlayerSessions
            .find { it.sessionId == sessionId }
            ?.markAsClosed()
    }

    /**
     * Fetch all game data to display a game to the 'Play Game' page.
     */
    suspend fun fetchGame(gameId: String): GetGameDataResponse {
        val gameRecord = pvpGameDaoService.fetchById(gameId)
            ?: throw NotFoundException("Game $gameId not found (fetch game)")

        val hasInvitee = gameRecord.invitee != null
        val inviteeUsername = if (hasInvitee) userCache.fetchUsernameOrDefault(gameRecord.invitee!!) else null
        val timeControl = gameRecord.timeControl()
        val timeRemaining =
            if (timeControl != null) {
                calculateTimeRemaining(gameId, timeControl)
            } else {
                null
            }

        return GetGameDataResponse(
            inviterId = gameRecord.inviter,
            inviterUsername = userCache.fetchUsernameOrDefault(gameRecord.inviter),
            inviterRating = gameRecord.inviterRatingFrom,
            inviterUserType = userCache.fetchUserType(gameRecord.inviter)!!,
            inviteeId = gameRecord.invitee,
            inviteeUsername = inviteeUsername,
            inviteeRating = gameRecord.inviteeRatingFrom,
            inviteeUserType = gameRecord.invitee?.let { userCache.fetchUserType(it) },
            inviterColor = gameRecord.inviterColor,
            created = gameRecord.created.toEpochMilliseconds(),
            isRated = gameRecord.isRated,
            fen = gameRecord.currentFen,
            moveIndex = gameRecord.currentHalfMoveIndex,
            timeControlCategory = gameRecord.timeControlCategory,
            timeControlBase = timeControl?.base,
            timeControlIncrement = timeControl?.increment,
            timeControlMode = gameRecord.timeControlMode,
            allowGuestsToJoin = gameRecord.allowGuestsToJoin,
            privateInvite = gameRecord.privateInvite,
            timeRemaining = timeRemaining,
            ratingUpdate = gameRecord.ratingUpdate(),
            gameEventType = gameRecord.gameStatus,
            outcome = gameRecord.outcome,
            drawPropositionUser = gameRecord.drawPropositionUser
        )
    }

    // TODO: use generic service instead
    suspend fun fetchMoveHistory(gameId: String): GameMovesResponse {
        return GameMovesResponse(pvpGameDaoService.listMoves(gameId))
    }

    suspend fun fetchChatHistory(gameId: String): GetChatMessageHistoryResponse {
        val messages = chatMessageDaoService
            .listAllMessages(gameId)
            .map { record -> mapRecordToDto(record) }

        return GetChatMessageHistoryResponse(messages)
    }

    private suspend fun fetchTimeRemaining(gameId: String): TimeRemaining {
        val timeControl = pvpGameDaoService.fetchTimeControl(gameId)
        if (timeControl == null) {
            throw BadRequestException("Game $gameId has no time control")
        } else {
            return calculateTimeRemaining(gameId, timeControl).normalize()
        }
    }

    private suspend fun calculateTimeRemaining(gameId: String, timeControl: TimeControlRecord): TimeRemaining {
        val joinTime = pvpGameDaoService.fetchJoinTime(gameId)
        val endTime = pvpGameDaoService.fetchGameEndTime(gameId)
        return calculateTimeRemaining(gameId, timeControl, joinTime, endTime)
    }

    private suspend fun calculateTimeRemaining(
        gameId: String,
        timeControl: TimeControlRecord,
        joinTime: Instant?,
        endTime: Instant?,
    ): TimeRemaining {
        fun timeElapsed(since: Instant): Long {
            return (endTime ?: Clock.System.now()).toEpochMilliseconds() - since.toEpochMilliseconds()
        }

        val baseMs = timeControl.base * 1_000L
        val incrementMs = timeControl.increment * 1_000L

        return if (joinTime != null) {
            when (timeControl.mode) {
                TimeControlMode.GAME_TIME -> {
                    var redTimeRemainingMs = baseMs
                    var blackTimeRemainingMs = baseMs
                    var previousTimestamp = joinTime
                    val moves = pvpGameDaoService.fetchTimedMoveHistory(gameId)

                    moves.forEach { move ->
                        val timeElapsed =
                            move.eventTime.toEpochMilliseconds() - previousTimestamp!!.toEpochMilliseconds()
                        if (move.position % 2 == 0) {
                            redTimeRemainingMs -= timeElapsed
                            redTimeRemainingMs += incrementMs
                        } else {
                            blackTimeRemainingMs -= timeElapsed
                            blackTimeRemainingMs += incrementMs
                        }
                        previousTimestamp = move.eventTime
                    }

                    val timeElapsed = timeElapsed(previousTimestamp!!)

                    if (moves.size % 2 == 0) {
                        redTimeRemainingMs -= timeElapsed
                    } else {
                        blackTimeRemainingMs -= timeElapsed
                    }

                    TimeRemaining(redTimeRemainingMs, blackTimeRemainingMs)
                }

                TimeControlMode.MOVE_TIME -> {
                    val lastMove = pvpGameDaoService.fetchLastMove(gameId)
                    val timeElapsed = timeElapsed(lastMove?.eventTime ?: joinTime)
                    val timeRemainingMs = baseMs - timeElapsed
                    if (lastMove == null || (lastMove.position + 1) % 2 == 0) {
                        TimeRemaining(timeRemainingMs, baseMs)
                    } else {
                        TimeRemaining(baseMs, timeRemainingMs)
                    }
                }
            }
        } else {
            TimeRemaining(baseMs, baseMs)
        }
    }

    suspend fun flagIfNeeded(gameId: String, timeControl: TimeControlRecord) {
        val logTag = "[$gameId][${timeControl.mode}]"
        val timeRemaining = calculateTimeRemaining(gameId, timeControl)
        if (Random.nextInt(0, 10) == 5) {
            logger.debug { "$logTag remaining ${timeRemaining.pretty()}" }
        }

        if (timeRemaining.red <= 0L && timeRemaining.red < timeRemaining.black) {
            logger.info { "$logTag red has flagged!" }
            flagByColor(gameId, RED)
        } else if (timeRemaining.black <= 0L && timeRemaining.black < timeRemaining.red) {
            logger.info { "$logTag black has flagged!" }
            flagByColor(gameId, BLACK)
        }
    }

    private suspend fun flagByColor(gameId: String, color: Color) {
        val userId = pvpGameDaoService.fetchUserIdForColor(gameId, color)
        if (userId == null) {
            logger.error { "$color user not found for $gameId" }
        } else {
            pvpGameDaoService.flag(
                userId = userId,
                gameId = gameId,
                winnerColor = color.reverse(),
                updateRatingsCallback = updateRatingsCallback
            )
            // TODO: send email when offline?
        }
    }

    /**
     * Cancel a game that has not been joined yet.
     */
    suspend fun cancel(userId: String, request: CancelGameRequest) {
        val gameId = request.gameId
        val gamePlayersStatus = fetchPlayersAndStatus(gameId)

        if (!gamePlayersStatus.isInviter(userId)) {
            throw ForbiddenException("$userId can not update game $gameId")
        } else if (gamePlayersStatus.status != CREATED) {
            throw BadRequestException("Can not cancel game $gameId in status ${gamePlayersStatus.status}")
        } else {
            pvpGameDaoService.updateStatus(userId, gameId, CANCELED)
        }

        discordService.gameCanceled(gameId)
    }

    suspend fun autoCancelGame(gameId: String) {
        pvpGameDaoService.updateStatus(null, gameId, AUTO_CANCELED)
    }

    suspend fun joinGame(userId: UserId, request: JoinGameRequest): JoinGameResponse {
        val gameId = request.gameId
        val gamePlayersStatus = fetchPlayersAndStatus(gameId)
        validateUserType(userId)

        if (gamePlayersStatus.inviterId == userId.id) {
            throw BadRequestException("You can not join your own games")
        } else if (gamePlayersStatus.hasInvitee() || gamePlayersStatus.status != CREATED) {
            throw BadRequestException("This game has already been joined")
        } else if (userId.userType == UserType.GUEST && !gamePlayersStatus.allowGuests) {
            throw BadRequestException("Guest users are not allowed to join this game")
        } else {
            val timeControlCategory = pvpGameDaoService.fetchTimeControlCategory(gameId)!!

            if (userId.userType == UserType.GUEST && timeControlCategory == TimeControlCategory.CORRESPONDENCE) {
                throw BadRequestException("Guest users are not allowed to join correspondence games")
            }

            var inviterColor = pvpGameDaoService.fetchInviterColor(gameId)
            val inviteeColor: Color
            if (inviterColor == null) {
                inviterColor = Color.random()
                inviteeColor = inviterColor.reverse()
                logger.debug { "[$gameId] has no color yet, $inviterColor to inviter and $inviteeColor to invitee" }
            } else {
                inviteeColor = inviterColor.reverse()
                logger.debug { "[$gameId] inviter had selected $inviterColor, invitee receives $inviteeColor" }
            }
            val userRating = getUserRating(userId.id, timeControlCategory)
            val timeControlRecord = pvpGameDaoService.fetchTimeControl(gameId)
            pvpGameDaoService.joinGame(
                userId = userId.id,
                userRating = userRating,
                gameId = gameId,
                updatedInviterColor = inviterColor,
                timeControlRecord = timeControlRecord,
                source = request.source,
                sourceId = request.sourceId
            )

            // if inviter has been offline for > N seconds, send email notification
            pvpGameDaoService
                .shouldSendGameJoinedNotification(gameId, NOTIFICATIONS_OFFLINE_FOR)
                ?.let { recipient ->
                    mailService.sendUserJoinedGameWhileOffline(
                        recipient = recipient,
                        inviteeUsername = userCache.fetchUsernameOrDefault(userId),
                        gameId = gameId
                    )
                }

            discordService.gameJoined(gameId)

            return JoinGameResponse(
                inviteeColor = inviteeColor,
                inviteeRating = userRating
            )
        }
    }

    suspend fun resign(userId: String, request: ResignGameRequest): ResignGameResponse {
        val gameId = request.gameId
        val gamePlayersStatus = fetchPlayersAndStatus(gameId)

        if (!gamePlayersStatus.isPlaying(userId)) {
            throw ForbiddenException("$userId can not update game $gameId")
        } else if (!gamePlayersStatus.isGameInProgress()) {
            throw BadRequestException("Can not resign game $gameId in status ${gamePlayersStatus.status}")
        } else {
            pvpGameDaoService.resign(userId, gameId, updateRatingsCallback)

            // if other player has been offline for > N seconds, send email notification
            pvpGameDaoService
                .shouldSendGameResignedNotification(
                    gameId = gameId,
                    userId = userId,
                    gamePlayersStatus,
                    NOTIFICATIONS_OFFLINE_FOR
                )
                ?.let { recipient ->
                    mailService.sendOpponentResignedWhileOffline(
                        recipient = recipient,
                        opponent = userCache.fetchUsernameOrDefault(userId),
                        gameId = gameId
                    )
                }

            return ResignGameResponse(fetchRatingUpdate(gameId))
        }
    }

    // TODO: check timing of last draw proposition sent (it can not be too often or it might be used to spam)
    suspend fun proposeDraw(userId: String, request: ProposeDrawRequest) {
        val gameId = request.gameId
        val gamePlayersStatus = fetchPlayersAndStatus(gameId)

        if (!gamePlayersStatus.isPlaying(userId)) {
            throw ForbiddenException("$userId can not update game $gameId")
        } else if (!gamePlayersStatus.isGameInProgress()) {
            throw BadRequestException("Can not draw game $gameId in status ${gamePlayersStatus.status}")
        } else {
            pvpGameDaoService.updateStatus(userId, gameId, DRAW_PROPOSED)

            // if other player has been offline for > N seconds, send email notification
            pvpGameDaoService
                .shouldSendOpponentProposedDrawNotification(
                    gameId = gameId,
                    userId = userId,
                    gamePlayersStatus,
                    NOTIFICATIONS_OFFLINE_FOR
                )
                ?.let { recipient ->
                    mailService.sendDrawProposedWhileOffline(
                        recipient = recipient,
                        opponent = userCache.fetchUsernameOrDefault(userId),
                        gameId = gameId
                    )
                }
        }
    }

    suspend fun respondToDraw(userId: String, request: RespondToDrawRequest) {
        val gameId = request.gameId
        val gamePlayersStatus = fetchPlayersAndStatus(gameId)

        if (!gamePlayersStatus.isPlaying(userId)) {
            throw ForbiddenException("$userId can not update game $gameId")
        } else if (gamePlayersStatus.status != DRAW_PROPOSED) {
            throw BadRequestException("Can not respond to draw in game $gameId if no draw was proposed")
        } else {
            val accept = request.accept
            val updatedStatus = if (accept) DRAW_ACCEPTED else DRAW_DECLINED
            val updateOutcome = if (accept) Outcome.DRAW else null
            pvpGameDaoService.updateStatus(userId, gameId, updatedStatus, updateOutcome)

            // if other player has been offline for > N seconds, send email notification
            pvpGameDaoService
                .shouldSendResponseToDrawNotification(
                    accept = accept,
                    gameId = gameId,
                    userId = userId,
                    gamePlayersStatus,
                    NOTIFICATIONS_OFFLINE_FOR
                )
                ?.let { recipient ->
                    mailService.sendOpponentRespondedToDrawWhileOffline(
                        recipient = recipient,
                        opponent = userCache.fetchUsernameOrDefault(userId),
                        accepted = accept,
                        gameId = gameId
                    )
                }
        }
    }

    suspend fun playMove(userId: String, request: PlayMoveRequest): PlayMoveResponse {
        val gameId = request.gameId
        val gamePlayersStatus = fetchPlayersAndStatus(gameId)

        if (!gamePlayersStatus.isPlaying(userId)) {
            throw ForbiddenException("$userId can not play in game $gameId")
        } else if (!gamePlayersStatus.isGameInProgress()) {
            throw BadRequestException("Can not play move in game $gameId in status ${gamePlayersStatus.status}")
        }

        val playMoveCallback: (Game) -> PlayMoveCallbackResult = { gameRecord ->
            // validate move can be parsed
            try {
                parseMoveFromUci(request.move)
            } catch (_: Exception) {
                logger.warn { "[${request.gameId}] Move '${request.move}' can not be parsed" }
                throw BadRequestException("Move '${request.move}' can not be parsed")
            }

            // validate move is legal
            if (!isMoveLegal(gameRecord.currentFen, request.move)) {
                logger.warn { "[${request.gameId}] Move '${request.move}' is illegal" }
                throw BadRequestException("Move '${request.move}' is illegal")
            }

            // validate that it is user's turn to play
            val userColor = gameRecord.userColor(userId)!!
            val colorToPlay = gameRecord.colorToPlay()
            if (userColor != colorToPlay) {
                throw BadRequestException("It is $colorToPlay turn to play and user $userId plays $userColor")
            }

            // process played move
            val updatedFen = calculateNewFen(gameRecord.currentFen, request.move)
            val updatedIndex = gameRecord.currentHalfMoveIndex + 1
            val opponentColor = userColor.reverse()
            var result = PlayMoveCallbackResult(updatedFen, updatedIndex)

            if (isCheckmated(updatedFen)) {
                result = result.copy(
                    newGameEventType = CHECKMATED,
                    outcome = userColor.asWinnerOutcome()
                )
            } else if (isStalemated(updatedFen)) {
                result = result.copy(
                    newGameEventType = STALEMATED,
                    outcome = userColor.asWinnerOutcome()
                )
            } else if (isInCheck(updatedFen, opponentColor)) {
                result = result.flagInCheck()
            }

            result
        }

        val dbResult =
            pvpGameDaoService.saveMove(
                userId = userId,
                gameId = request.gameId,
                move = request.move,
                playMoveCallback = playMoveCallback,
                hasViolatedPerpetualCheckingRule = { moves -> hasViolatedPerpetualCheckingRuleCallback(moves) },
                updateRatingsCallback = updateRatingsCallback
            )

        return when (dbResult) {
            is TryEither.Valid -> {
                // add Discord reaction when reached move 3
                if (dbResult.value.newPosition == 6) {
                    discordService.gameReachedMove3(gameId)
                }

                // if opponent has been offline for > N minutes, send email notification
                pvpGameDaoService
                    .shouldSendOpponentPlayedMoveNotification(
                        gameId = gameId,
                        userId = userId,
                        gamePlayersStatus,
                        NOTIFICATIONS_OFFLINE_FOR
                    )
                    ?.let { recipient ->
                        mailService.sendOpponentPlayedMoveWhileOffline(
                            recipient = recipient,
                            opponent = userCache.fetchUsernameOrDefault(userId),
                            gameId = gameId
                        )
                    }

                // build response
                val playMoveResult = dbResult.value
                val ratingUpdate = playMoveResult.newGameEventType
                    ?.let { gameEventType -> fetchRatingUpdateIfNecessary(gameId, gameEventType) }

                PlayMoveResponse(
                    move = request.move,
                    updatedIndex = playMoveResult.newPosition,
                    updatedFen = playMoveResult.newFen,
                    gameEventType = playMoveResult.newGameEventType,
                    ratingUpdate = ratingUpdate
                )
            }

            is TryEither.Invalid -> throw dbResult.exception
            else -> throw InternalErrorException("Unexpected result")
        }
    }

    /**
     * Moves must include last one played
     */
    private fun hasViolatedPerpetualCheckingRuleCallback(moves: List<String>): PerpetualCheckingCallbackResult? {
        val board = Board(keepHistory = true)
        board.registerMoves(moves.map { parseMoveFromUci(it) })
        val playerColor = board.colorToPlay().reverse()
        val history = board.getHistory()!!
        val map = history.findSequencesOfConsecutiveChecks(playerColor)
        if (map.isNotEmpty()) {
            map.toList().sortedBy { (key, _) -> key }.forEach { (key, sequence) ->
                val fullMovesStr = sequence.fullMoves().joinToString(", ")
                logger.info { "[$key] ${sequence.attackers} / $fullMovesStr [${sequence.size()}]" }
                perpetualCheckRules.forEach { rule ->
                    val hasExceeded = sequence.exceeds(rule)
                    logger.info { "exceeds $rule -> $hasExceeded" }
                    if (hasExceeded) {
                        return PerpetualCheckingCallbackResult(PERPETUAL_CHECKING, playerColor.asLoserOutcome())
                    }
                }
            }
        }

        return null
    }

    /**
     * Only fetch the necessary fields to proceed to the basic requests validation
     */
    private suspend fun fetchPlayersAndStatus(gameId: String) =
        pvpGameDaoService.fetchPlayersAndStatus(gameId) ?: throw NotFoundException("Game $gameId not found")

    private suspend fun getUserRating(userId: String, timeControlCategory: TimeControlCategory) =
        pvpGameDaoService.fetchRatingForUser(userId, timeControlCategory) ?: 0

    private suspend fun fetchRatingUpdateIfNecessary(gameId: String, gameEventType: GameEventType): RatingUpdate? =
        if (gameEndedStatuses.contains(gameEventType)) {
            fetchRatingUpdate(gameId)
        } else {
            null
        }

    private suspend fun fetchRatingUpdateIfNecessaryWs(
        gameId: String,
        gameEventType: GameEventType
    ): RatingUpdateWs? {
        fun mapToWsRatingUpdate(gameRatingUpdate: RatingUpdate?): RatingUpdateWs? =
            gameRatingUpdate?.let {
                RatingUpdateWs(
                    isRated = it.isRated,
                    inviterRatingFrom = it.inviterRatingFrom,
                    inviterRatingTo = it.inviterRatingTo,
                    inviteeRatingFrom = it.inviteeRatingFrom,
                    inviteeRatingTo = it.inviteeRatingTo,
                )
            }

        return if (gameEndedStatuses.contains(gameEventType)) {
            mapToWsRatingUpdate(fetchRatingUpdate(gameId))
        } else {
            null
        }
    }

    private suspend fun fetchRatingUpdate(gameId: String) =
        pvpGameDaoService.fetchRatingUpdate(gameId)?.ratingUpdate()

    private fun isOnline(userId: String?) =
        userId?.let { userService.isOnline(it) } ?: false

    private suspend fun validateUserType(userId: UserId) =
        validateUserType(userId.id, userId.userType)

    // guaranteed by token but safer for unit testing
    // maybe add a flag so it's only checked in unit tests?
    private suspend fun validateUserType(userId: String, userType: UserType) {
        require(userType == userCache.fetchUserType(userId)) {
            "Inconsistent user type for $userId, expected ${userCache.fetchUserType(userId)} but received $userType"
        }
    }

    private val updateRatingsCallback: (
        gameRecord: Game,
        outcome: Outcome,
        inviterCurrentRating: Int,
        inviteeCurrentRating: Int,
    ) -> UpdateRatingsCallbackResult? =
        { gameRecord, outcome, inviterCurrentRating, inviteeCurrentRating ->
            if (gameRecord.isRated && outcome != Outcome.DRAW) {
                val k = gameRecord.kValue
                val isInviterWinner =
                    (outcome == RED_WINS && gameRecord.inviterColor == RED)
                            || (outcome == BLACK_WINS && gameRecord.inviterColor == BLACK)

                if (isInviterWinner) {
                    val elo = calculateElo(
                        winnerRating = inviterCurrentRating,
                        loserRating = inviteeCurrentRating,
                        k = k
                    )

                    UpdateRatingsCallbackResult(
                        inviterNewRating = elo.winnerNewRating,
                        inviteeNewRating = elo.loserNewRating
                    )
                } else {
                    val elo = calculateElo(
                        winnerRating = inviteeCurrentRating,
                        loserRating = inviterCurrentRating,
                        k = k
                    )

                    UpdateRatingsCallbackResult(
                        inviterNewRating = elo.loserNewRating,
                        inviteeNewRating = elo.winnerNewRating
                    )
                }
            } else {
                null
            }
        }

    private suspend fun mapToGameToPlayDto(
        game: Game,
        userId: String,
        onlineUserIds: Set<String>
    ): GameToPlay {
        return GameToPlay(
            gameId = game.id,
            isRated = game.isRated,
            opponentUserId = userId,
            opponentUserType = userCache.fetchUserType(userId),
            opponentUsername = userCache.fetchUsernameOrDefault(userId),
            opponentColor = game.userColor(userId),
            opponentRating = game.inviterRatingFrom,
            isOpponentOnline = onlineUserIds.contains(userId),
            timeControlCategory = game.timeControlCategory,
            timeControlBase = game.timeControlBase,
            timeControlIncrement = game.timeControlIncrement,
            allowGuests = game.allowGuestsToJoin,
            lastUpdated = game.lastUpdated.toEpochMilliseconds()
        )
    }

    private suspend fun mapRecordToDto(message: GameChatMessage): ChatMessage {
        return ChatMessage(
            index = message.index,
            author = ChatMessage.Author(
                userId = message.author,
                username = userCache.fetchUsernameOrDefault(message.author),
                userType = userCache.fetchUserType(message.author)!!,
            ),
            messageTime = message.messageTime.toEpochMilliseconds(),
            content = message.content
        )
    }

    private companion object {

        val NOTIFICATIONS_OFFLINE_FOR = 2.minutes
        const val MESSAGE_LENGTH_LIMIT = 200
        const val MAX_CREATED_GAMES_PER_SETTINGS = 3

        fun formatMillis(millis: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(millis).toInt()
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis).toInt() - hours * 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis).toInt() - hours * 3600 - minutes * 60
            val millisRemainder = millis - hours * 3600 * 1000 - minutes * 60 * 1000 - seconds * 1000
            return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millisRemainder)
        }

        fun TimeRemaining.pretty(): String {
            return "red " + formatMillis(this.red) + " vs. black " + formatMillis(this.black)
        }

    }

}
