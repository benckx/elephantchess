package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.callback.BotMove
import io.elephantchess.db.callback.PlayMoveBotGameCallbackResult
import io.elephantchess.db.callback.PlayMoveBotGameCallbackResult.ErrorType.BOT_MOVE_NOT_FOUND
import io.elephantchess.db.callback.PlayMoveBotGameCallbackResult.ErrorType.ILLEGAL_PLAYER_MOVE
import io.elephantchess.db.dao.codegen.tables.pojos.BotGame
import io.elephantchess.db.dao.codegen.tables.pojos.BotGameStatusEvent
import io.elephantchess.db.model.BotGameStatusRecord
import io.elephantchess.db.services.OpeningRepositoryCacheDaoService
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.utils.generateId
import io.elephantchess.db.utils.nextMove
import io.elephantchess.db.utils.plusSeconds
import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.protocol.model.InfoLinesResult
import io.elephantchess.model.BotGameMoveType
import io.elephantchess.model.Engine
import io.elephantchess.model.GameEventType.*
import io.elephantchess.model.OpeningMode
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.botgame.*
import io.elephantchess.servicelayer.dto.gamedata.GameMovesResponse
import io.elephantchess.servicelayer.dto.ws.BotGameSpectatorUpdate
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.exceptions.ForbiddenException
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.exceptions.RequestTimeoutException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.ws.PvbWebSocketSession
import io.elephantchess.servicelayer.utils.modelToProcess
import io.elephantchess.servicelayer.utils.ops.isNonStandardFen
import io.elephantchess.servicelayer.utils.ops.launchAtFixedRate
import io.elephantchess.servicelayer.utils.ops.safeQueryForDepth
import io.elephantchess.utils.selectByProbability
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.calculateNewFen
import io.elephantchess.xiangqi.Board.Companion.colorToPlay
import io.elephantchess.xiangqi.Board.Companion.isCheckmated
import io.elephantchess.xiangqi.Board.Companion.isStalemated
import io.elephantchess.xiangqi.Board.Companion.moveToFens
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import io.elephantchess.xiangqi.Board.Companion.validateFen
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.Variant
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ChannelResult
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PlayerVsBotGameService(
    private val enginesPool: EnginePool,
    private val pvbGameDaoService: PlayerVsBotGameDaoService,
    private val openingRepositoryDaoService: OpeningRepositoryCacheDaoService,
    private val userCache: UserCache,
    appConfig: AppConfig,
    refresherScope: CoroutineScope,
    private val logger: KLogger,
) {

    private val pikafishVersion = appConfig.pikafishVersion
    private val fairyStockfishVersion = appConfig.fairyStockfishVersion

    private val sessionsRefresh = 4.seconds
    private val wsSessions = mutableListOf<PvbWebSocketSession>()

    private val refreshJob = launchAtFixedRate(
        scope = refresherScope,
        initialDelay = sessionsRefresh,
        period = sessionsRefresh,
        action = {
            if (wsSessions.isNotEmpty()) {
                // fetch new moves from SQL in a single query
                val tuples = wsSessions.map { session -> session.gameId to session.currentMoveIndex }
                val newMoves = pvbGameDaoService.fetchNewMovesForGamesAndIndexes(tuples)
                val updatedGameIds = newMoves.map { move -> move.botGameId }.distinct()
                val gameStatusMap = pvbGameDaoService.fetchCurrentStatusOfGames(updatedGameIds)

                // send the new moves to the sessions
                newMoves
                    .groupBy { record -> record.botGameId }
                    .map { (gameId, moveRecords) ->
                        gameId to moveRecords.sortedBy { it.position }
                    }
                    .forEach { (gameId, moveRecords) ->
                        wsSessions
                            .filter { session -> session.gameId == gameId }
                            .forEach { session ->
                                val (status, outcome) = gameStatusMap[gameId] ?: (CREATED to null)
                                session.update(
                                    BotGameSpectatorUpdate(
                                        moveIndex = moveRecords.last().position + 1,
                                        newMoves = moveRecords.map { record -> record.uci },
                                        status = status,
                                        outcome = outcome,
                                    )
                                )
                            }
                    }

                // remove the sessions that are not active anymore
                wsSessions.removeIf { session ->
                    if (session.isClosed) logger.debug { "removing $session" }
                    session.isClosed
                }
            }
        }
    )

    fun cancel() {
        refreshJob.cancel()
    }

    suspend fun create(userId: UserId, request: CreateBotGameRequest): CreateBotGameResponse {
        if (request.depth !in 1..14) {
            throw BadRequestException("Depth must be between 1 and 14")
        }

        if (userId.userType == UserType.GUEST && request.depth > 6) {
            throw BadRequestException("You must be logged in to play with depth greater than 6")
        }

        // Manchu variant requires Fairy Stockfish
        if (request.variant == Variant.MANCHU && request.engine == Engine.PIKAFISH) {
            throw BadRequestException("Pikafish does not support the Manchu variant. Please use Fairy Stockfish.")
        }

        request.startFen?.let { fen ->
            try {
                validateFen(fen)
            } catch (e: IllegalArgumentException) {
                throw BadRequestException(e.message ?: "Invalid FEN")
            }

            if (isCheckmated(fen)) {
                throw BadRequestException("Start position is checkmate")
            } else if (isStalemated(fen)) {
                throw BadRequestException("Start position is stalemate")
            }
        }

        val now = Clock.System.now()
        val gameId = generateId()

        val actualStartFen = request.startFen ?: Board.defaultStartFen(request.variant)
        val usesDefaultStartFen = actualStartFen == Board.defaultStartFen(request.variant)

        // Manchu variant requires Fairy Stockfish.
        // If Pikafish is requested but the start FEN is non-standard, safeQueryForDepth will
        // use Fairy Stockfish instead, so we persist the effective engine/version that will be used.

        val isVariant = request.variant == Variant.MANCHU
        val isUnsupportedByPikafish = isNonStandardFen(actualStartFen)
        val effectiveEngine = if (isVariant || isUnsupportedByPikafish) Engine.FAIRYSTOCKFISH else request.engine
        val engineVersion = when (effectiveEngine) {
            Engine.PIKAFISH -> pikafishVersion
            Engine.FAIRYSTOCKFISH -> fairyStockfishVersion
        }

        val gameRecord = BotGame()
        gameRecord.id = gameId
        gameRecord.userId = userId.id
        gameRecord.userColor = request.color
        gameRecord.engine = effectiveEngine
        gameRecord.engineVersion = engineVersion
        gameRecord.depth = request.depth
        gameRecord.startFen = if (usesDefaultStartFen) null else actualStartFen
        gameRecord.gameStatus = CREATED
        gameRecord.currentFen = actualStartFen
        gameRecord.currentHalfMoveIndex = 0
        gameRecord.created = now
        gameRecord.lastUpdated = now
        gameRecord.openingMode = request.openingMode
        gameRecord.variant = request.variant

        val statusRecord = BotGameStatusEvent()
        statusRecord.botGameId = gameId
        statusRecord.eventType = CREATED
        statusRecord.eventTime = now

        pvbGameDaoService.insertGame(gameRecord, statusRecord)

        if (colorToPlay(actualStartFen) != request.color) {
            val botMove = playBotMove(
                gameId = gameId,
                botColor = request.color.reverse(),
                userMove = null,
                fen = actualStartFen,
                startFen = actualStartFen,
                position = 0,
                engine = effectiveEngine,
                depth = request.depth,
                usesDefaultStartFen = usesDefaultStartFen,
                openingMode = request.openingMode,
                variant = request.variant,
            )

            if (botMove != null) {
                pvbGameDaoService.saveFirstBotMove(gameId, botMove, calculateNewFen(actualStartFen, botMove.uci))
            } else {
                // FIXME: the game is created but is in a wrong state -> maybe it should be canceled
                throw RequestTimeoutException("Engine did not respond in time, please retry later")
            }
        }

        return CreateBotGameResponse(gameId)
    }

    suspend fun listUserGames(userId: String, beforeTs: Long?): ListUserBotGamesDto {
        return pvbGameDaoService
            .listGamesByUserId(userId, 30, beforeTs)
            .map { record ->
                ListUserBotGamesDto.Entry(
                    gameId = record.id,
                    color = record.userColor,
                    engine = record.engine,
                    depth = record.depth,
                    customStartFen = record.startFen != null,
                    currentFen = record.currentFen ?: DEFAULT_START_FEN,
                    status = record.gameStatus,
                    outcome = record.outcome,
                    moveIndex = record.currentHalfMoveIndex,
                    created = record.created.toEpochMilliseconds(),
                    lastUpdated = record.lastUpdated.toEpochMilliseconds(),
                    variant = record.variant ?: Variant.XIANGQI,
                )
            }
            .let { entries ->
                ListUserBotGamesDto(entries)
            }
    }

    suspend fun fetchGameData(gameId: String): GetBotGameDataResponse {
        val game = pvbGameDaoService.fetchById(gameId)
        if (game == null) {
            throw NotFoundException("Game $gameId not found")
        } else {
            return GetBotGameDataResponse(
                userId = game.userId,
                username = game.userId?.let { userCache.fetchUsernameOrDefault(it) },
                userType = game.userId?.let { userCache.fetchUserType(it) },
                userColor = game.userColor,
                engine = game.engine,
                depth = game.depth,
                startFen = game.startFen ?: Board.defaultStartFen(game.variant ?: Variant.XIANGQI),
                status = game.gameStatus,
                moveIndex = game.currentHalfMoveIndex,
                fen = game.currentFen,
                created = game.created.toEpochMilliseconds(),
                lastUpdated = game.lastUpdated.toEpochMilliseconds(),
                outcome = game.outcome,
                variant = game.variant,
            )
        }
    }

    // TODO: use generic service instead
    suspend fun fetchMoveHistory(gameId: String): GameMovesResponse {
        validateGameExists(gameId)
        return GameMovesResponse(pvbGameDaoService.listMoves(gameId))
    }

    suspend fun playMove(userId: String, request: PlayMoveBotGameRequest): PlayMoveBotGameResponse {
        validateCanPlay(userId, request.gameId)
        var result = PlayMoveBotGameCallbackResult()

        // TODO: use same pattern as in GameService with TryEither
        pvbGameDaoService.saveUserMoveResult(request.gameId, request.move) { gameRecord, userMove ->
            val variant = gameRecord.variant
            val userColor = gameRecord.userColor
            val botColor = userColor.reverse()

            val isLegalMove = Board.isMoveLegal(gameRecord.currentFen, userMove)

            if (isLegalMove) {
                // user plays their move
                val board = Board(gameRecord.currentFen)
                board.registerMove(userMove)

                val isBotCheckmated = board.isCheckmated(botColor)
                val isBotStalemated = board.isStalemated(botColor)

                if (isBotCheckmated || isBotStalemated) {
                    // user has won against the bot
                    result = result.updateForUserVictory(userColor, isBotCheckmated, isBotStalemated)
                    result = result.copy(newPosition = gameRecord.currentHalfMoveIndex + 1)
                } else {
                    // otherwise, bot plays its move
                    val botMove = playBotMove(
                        gameId = request.gameId,
                        botColor = botColor,
                        userMove = userMove,
                        fen = board.outputFen(),
                        startFen = gameRecord.startFen ?: Board.defaultStartFen(variant),
                        position = gameRecord.currentHalfMoveIndex,
                        engine = gameRecord.engine,
                        depth = gameRecord.depth,
                        usesDefaultStartFen = gameRecord.startFen == null,
                        openingMode = gameRecord.openingMode,
                        variant = variant,
                    )

                    if (botMove != null) {
                        if (!Board.isMoveLegal(board.outputFen(), botMove.uci)) {
                            logger.error { "[${request.gameId}] bot returned illegal move ${botMove.uci} for FEN ${board.outputFen()}" }
                            result = result.addError(BOT_MOVE_NOT_FOUND)
                        } else {
                            board.registerMove(botMove.uci)
                            logger.debug { "FEN after bot move: ${board.outputFen()}" }
                            result = result.copy(botMove = botMove)

                            val isUserCheckmated = board.isCheckmated(userColor)
                            val isUserStalemated = board.isStalemated(userColor)

                            if (isUserCheckmated || isUserStalemated) {
                                // bot has won against the player
                                result = result.updateForBotVictory(botColor, isUserCheckmated, isUserStalemated)
                            }

                            result = result.copy(newPosition = gameRecord.currentHalfMoveIndex + 2)
                        }
                    } else {
                        result = result.addError(BOT_MOVE_NOT_FOUND)
                    }
                }

                result = result.copy(newFen = board.outputFen())
            } else {
                result = result.addError(ILLEGAL_PLAYER_MOVE)
            }

            result
        }

        if (result.hasError(BOT_MOVE_NOT_FOUND)) {
            throw RequestTimeoutException("Engine did not respond in time, reload page")
        } else if (result.hasError(ILLEGAL_PLAYER_MOVE)) {
            throw BadRequestException("Illegal move ${request.move}")
        } else {
            // here we could also directly refresh spectator sessions
            // it would only affect this pod though
            return PlayMoveBotGameResponse(
                fen = result.newFen!!,
                position = result.newPosition!!,
                botMove = result.botMove?.uci,
                statusUpdate = result.gameEventType,
            )
        }
    }

    private suspend fun playBotMove(
        gameId: String,
        userMove: String?,
        botColor: Color,
        startFen: String,
        fen: String,
        position: Int,
        engine: Engine,
        depth: Int,
        usesDefaultStartFen: Boolean,
        openingMode: OpeningMode,
        variant: Variant = Variant.XIANGQI,
    ): BotMove? {
        suspend fun playWithEngine(): BotMove? {
            return playWithEngine(gameId, botColor, startFen, fen, position, engine, depth, variant)
        }

        val canUseOpeningRepository =
            usesDefaultStartFen && position <= REPO_MAX_POSITION_INDEX && variant == Variant.XIANGQI
                && openingMode != OpeningMode.ENGINE_ONLY

        return if (canUseOpeningRepository) {
            playFromOpeningRepository(gameId, userMove, openingMode) ?: playWithEngine()
        } else {
            playWithEngine()
        }
    }

    private suspend fun playFromOpeningRepository(
        gameId: String,
        userMove: String?,
        openingMode: OpeningMode
    ): BotMove? {
        val moves = pvbGameDaoService.listMoves(gameId) + listOfNotNull(userMove)
        val openingEntries = openingRepositoryDaoService.fetchNextMovesData(moves)

        return if (openingEntries.isNotEmpty()) {
            val randomEntry =
                if (openingMode == OpeningMode.RANDOM) {
                    openingEntries.random()
                } else {
                    selectByProbability(openingEntries) { it.occurrences }
                }
            logger.debug {
                val totalOccurrences = openingEntries.sumOf { it.occurrences }
                "[$gameId] playing from opening repository ${randomEntry.occurrences} / $totalOccurrences"
            }
            BotMove(randomEntry.nextMove(), BotGameMoveType.OPENING)
        } else {
            null
        }
    }

    private suspend fun playWithEngine(
        gameId: String,
        botColor: Color,
        startFen: String,
        fen: String,
        position: Int,
        engine: Engine,
        depth: Int,
        variant: Variant,
    ): BotMove? {
        suspend fun queryEngine(fenToEngine: String): InfoLinesResult? =
            enginesPool.safeQueryForDepth(
                fen = fenToEngine,
                engineId = modelToProcess(engine),
                depth = depth,
                timeout = 15_000,
                variant = variant
            )

        suspend fun findAlternativeMove(bestMove: String): String? {
            val cpMultiplier = if (botColor == RED) 1 else -1
            val movesEvaluation = Board(fen)
                .listLegalMoves(botColor)
                .filterNot { move -> move.toUci() == bestMove }
                .shuffled()
                .take(LEGAL_MOVES_TO_EVAL_FOR_ALTERNATIVE)
                .map { move -> move to calculateNewFen(fen, move.toUci()) }
                .mapNotNull { (move, moveFen) ->
                    queryEngine(moveFen)?.deepestResultCentiPawns()?.let { score ->
                        move.toUci() to score * cpMultiplier
                    }
                }
                .toMap()

            if (logger.isDebugEnabled()) {
                movesEvaluation.forEach { (move, cp) ->
                    logger.debug { "[$gameId] move $move has score $cp" }
                }
            }

            return movesEvaluation.maxByOrNull { it.value }?.let { (move, cp) ->
                logger.info { "[$gameId] found alternative move $move with cp $cp" }
                move
            }
        }

        suspend fun validateForRepetitions(bestMove: String): String {
            if (position >= MIN_MOVE_INDEX_CHECK_REPETITION) {
                val board = Board(fen)
                board.registerMove(bestMove)
                if (board.isInCheck(botColor.reverse())) {
                    logger.debug { "user in check -> check for repetitions" }
                    val bestMoveFen = resetFullMoveCount(board.outputFen())
                    val movesHistory = pvbGameDaoService.listMoves(gameId)
                    val historyFens = moveToFens(movesHistory, startFen)
                        .takeLast(POSITIONS_TO_CONSIDER_TO_AVOID_REPETITIONS)
                        .map { resetFullMoveCount(it) }
                    val botFens = when (botColor) {
                        RED -> historyFens.filter { it.contains(" b ") }
                        BLACK -> historyFens.filter { it.contains(" w ") }
                    }
                    val hasRepetition = botFens.contains(bestMoveFen)
                    if (hasRepetition) {
                        logger.debug { "[$gameId] repetition detected, looking for alternative move" }
                        findAlternativeMove(bestMove)?.let { return it }
                    }
                }
            }

            return bestMove
        }

        queryEngine(fen)?.bestMove?.let { bestMove ->
            return BotMove(validateForRepetitions(bestMove), BotGameMoveType.ENGINE)
        }

        return null
    }

    suspend fun cancel(userId: String, request: CancelBotGameRequest) {
        val gameId = request.gameId
        val gameStatus = validateCanPlay(userId, gameId)

        if (gameStatus.canCancel()) {
            pvbGameDaoService.updateGameStatus(gameId, CANCELED)
        } else {
            throw BadRequestException("Game $gameId can not be canceled anymore")
        }
    }

    suspend fun resign(userId: String, request: ResignBotGameRequest) {
        val gameId = request.gameId
        val gameStatus = validateCanPlay(userId, gameId)
        pvbGameDaoService.updateGameStatus(gameId, RESIGNED, gameStatus.userColor.reverse())
    }

    suspend fun autoResign(gameId: String, delay: Duration) {
        val gameStatus = pvbGameDaoService.fetchById(gameId)
        val eventTime = gameStatus!!.lastUpdated.plusSeconds(delay.inWholeSeconds)
        pvbGameDaoService.updateGameStatus(gameId, AUTO_RESIGNED, gameStatus.userColor.reverse(), eventTime)
    }

    private suspend fun validateCanPlay(userId: String, gameId: String): BotGameStatusRecord {
        val gameStatus = pvbGameDaoService.fetchGameStatus(gameId)
        if (gameStatus == null) {
            throw NotFoundException("Game $gameId not found")
        } else {
            if (gameStatus.userId != null && gameStatus.userId != userId) {
                throw ForbiddenException("User $userId is not allowed to play in game $gameId")
            } else if (gameStatus.userId == null) {
                throw ForbiddenException("Game $gameId is anonymous therefore $userId is not allowed to play in it")
            }

            if (!gameStatus.isInProgress()) {
                throw BadRequestException("Game $gameId is not in progress anymore and can not be updated")
            }
        }

        return gameStatus
    }

    private suspend fun validateGameExists(gameId: String) {
        pvbGameDaoService.fetchGameStatus(gameId) ?: throw NotFoundException("Game $gameId not found")
    }

    fun startSpectatorSession(
        gameId: String,
        moveIndex: Int,
        sendCb: (BotGameSpectatorUpdate) -> ChannelResult<Unit>,
    ): String {
        val session = PvbWebSocketSession(sendCb, gameId, moveIndex)
        wsSessions.add(session)
        logger.debug { "created $session" }
        return session.sessionId
    }

    fun closeSpectatorSession(sessionId: String) {
        wsSessions
            .find { it.sessionId == sessionId }
            ?.markAsClosed()
    }

    private companion object {

        const val REPO_MAX_POSITION_INDEX = 10

        // avoid repetition
        const val MIN_MOVE_INDEX_CHECK_REPETITION = 6 // after move 3
        const val POSITIONS_TO_CONSIDER_TO_AVOID_REPETITIONS = 10
        const val LEGAL_MOVES_TO_EVAL_FOR_ALTERNATIVE = 12

    }

}
