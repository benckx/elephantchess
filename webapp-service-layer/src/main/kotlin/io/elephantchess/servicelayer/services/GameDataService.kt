package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.tables.pojos.BotGame
import io.elephantchess.db.dao.codegen.tables.pojos.Game
import io.elephantchess.db.dao.codegen.tables.pojos.MoveAnalysis
import io.elephantchess.db.dao.codegen.tables.pojos.ReferenceGame
import io.elephantchess.db.services.*
import io.elephantchess.db.utils.*
import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.PikafishEngineId
import io.elephantchess.engines.protocol.model.InfoLineResult
import io.elephantchess.engines.protocol.model.InfoLineResult.Companion.parseInfoLine
import io.elephantchess.model.*
import io.elephantchess.model.AnalysisStatus.*
import io.elephantchess.model.GameType.*
import io.elephantchess.servicelayer.dto.analysis.GameAnalysisResponse
import io.elephantchess.servicelayer.dto.analysis.GameAnalysisStatusResponse
import io.elephantchess.servicelayer.dto.analysis.StartGameAnalysisResponse
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto.Companion.mapToInfoLineResultDto
import io.elephantchess.servicelayer.dto.gamedata.GameMetadataDto
import io.elephantchess.servicelayer.dto.gamedata.GameMovesResponse
import io.elephantchess.servicelayer.dto.gamedata.ListLastGamesResponse
import io.elephantchess.servicelayer.dto.lobby.LatestGamesUpdateRequest
import io.elephantchess.servicelayer.dto.lobby.LatestGamesUpdateResponse
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.utils.ops.safeQueryForDepth
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.Variant
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Generic service that handles game and analysis data for games of all [GameType]
 */
class GameDataService(
    private val appConfig: AppConfig,
    private val enginesPool: EnginePool,
    private val databaseService: DatabaseService,
    private val engineCacheService: EngineCacheService,
    private val moveAnalysisDaoService: MoveAnalysisDaoService,
    private val pvbGameDaoService: PlayerVsBotGameDaoService,
    private val pvpGameDaoService: PlayerVsPlayerGameDaoService,
    private val referenceGameDaoService: ReferenceGameDaoService,
    private val referencePlayerDaoService: ReferencePlayerDaoService,
    private val referenceEventDaoService: ReferenceEventDaoService,
    private val userDaoService: UserDaoService,
    private val userService: UserService,
    private val userCache: UserCache,
    private val logger: KLogger,
) {

    private val analysisScope by lazy {
        CoroutineScope(Dispatchers.IO.limitedParallelism(2))
    }

    suspend fun countTotalGames(): Int {
        return referenceGameDaoService.countAllGames() +
                pvbGameDaoService.countTotalGames(MIN_MOVE_INDEX) +
                pvpGameDaoService.countTotalGames(MIN_MOVE_INDEX)
    }

    suspend fun countTotalAppGames(): Int {
        return pvbGameDaoService.countTotalGames(MIN_MOVE_INDEX) +
                pvpGameDaoService.countTotalGames(MIN_MOVE_INDEX)
    }

    suspend fun countTotalManchuGames(): Int {
        return pvbGameDaoService.countManchuGames(MIN_MOVE_INDEX) +
                pvpGameDaoService.countManchuGames(MIN_MOVE_INDEX)
    }

    /**
     * Half moves or plies
     */
    suspend fun countTotalMoves(): Map<GameType, Int> {
        return mapOf(
            PVP to pvpGameDaoService.countTotalMoves(),
            PVB to pvbGameDaoService.countTotalMoves(),
            DB to referenceGameDaoService.countTotalMoves()
        )
    }

    suspend fun latestMoveAnalysisUpdate(): Instant? {
        return moveAnalysisDaoService.fetchLatestMoveAnalysis()
    }

    suspend fun fetchAnalysisStatusOfGame(gameId: GameId): AnalysisStatus =
        moveAnalysisDaoService.fetchAnalysisStatus(gameId) ?: throw NotFoundException("$gameId not found")

    suspend fun fetchGameMetadata(gameId: GameId): GameMetadataDto {
        val gameMetadataDto =
            when (gameId.type) {
                PVP -> {
                    pvpGameDaoService
                        .fetchById(gameId.id)
                        ?.let { record ->
                            val redPlayerId = if (record.inviterColor == RED) record.inviter else record.invitee
                            val blackPlayerId = if (record.inviterColor == BLACK) record.inviter else record.invitee
                            val redPlayerName = fetchUserName(redPlayerId) ?: "?"
                            val blackPlayerName = fetchUserName(blackPlayerId) ?: "?"

                            GameMetadataDto(
                                gameId = gameId,
                                lastUpdated = record.lastUpdated.toEpochMilliseconds(),
                                redPlayerId = redPlayerId,
                                redPlayerName = redPlayerName,
                                blackPlayerId = blackPlayerId,
                                blackPlayerName = blackPlayerName,
                                finalFen = record.currentFen,
                                outcome = record.outcome,
                                analysisStatus = record.analysisStatus,
                                variant = record.variant,
                            )
                        }
                }

                PVB -> {
                    pvbGameDaoService
                        .fetchById(gameId.id)
                        ?.let { record ->
                            val userId = record.userId
                            val userName = fetchUserName(userId)
                            var blackPlayerId: String? = null
                            var blackPlayerName: String? = null
                            var redPlayerId: String? = null
                            var redPlayerName: String? = null
                            val engineName = record.prettyEngineName()

                            when (record.userColor) {
                                RED -> {
                                    redPlayerId = userId
                                    redPlayerName = userName
                                    blackPlayerName = engineName
                                }

                                BLACK -> {
                                    redPlayerName = engineName
                                    blackPlayerId = userId
                                    blackPlayerName = userName
                                }

                                else -> {
                                    // should not happen, but in case of data inconsistency, we don't want to fail the whole request
                                    logger.warn { "Game $gameId has invalid user color ${record.userColor}" }
                                }
                            }

                            GameMetadataDto(
                                gameId = gameId,
                                lastUpdated = record.created.toEpochMilliseconds(),
                                redPlayerId = redPlayerId,
                                redPlayerName = redPlayerName,
                                blackPlayerId = blackPlayerId,
                                blackPlayerName = blackPlayerName,
                                startFen = record.startFen,
                                finalFen = record.currentFen,
                                outcome = record.outcome,
                                analysisStatus = record.analysisStatus,
                                engine = record.engine,
                                depth = record.depth,
                                variant = record.variant
                            )
                        }
                }

                DB -> {
                    referenceGameDaoService
                        .findById(gameId.id)
                        ?.let { record ->
                            val redPlayerName =
                                when (record.redPlayer) {
                                    null -> null
                                    else -> referencePlayerDaoService.findCanonicalPlayerName(record.redPlayer)
                                }

                            val blackPlayerName =
                                when (record.blackPlayer) {
                                    null -> null
                                    else -> referencePlayerDaoService.findCanonicalPlayerName(record.blackPlayer)
                                }

                            val event =
                                record.event?.let { eventId -> referenceEventDaoService.fetchEventById(eventId) }

                            GameMetadataDto(
                                gameId = gameId,
                                lastUpdated = record.date?.atStartOfDay()?.toUtcInstant()?.toEpochMilliseconds(),
                                redPlayerId = record.redPlayer,
                                redPlayerName = redPlayerName,
                                blackPlayerId = record.blackPlayer,
                                blackPlayerName = blackPlayerName,
                                finalFen = record.finalFen,
                                outcome = record.outcome,
                                analysisStatus = record.analysisStatus,
                                eventId = event?.id,
                                eventName = event?.name,
                                variant = Variant.XIANGQI
                            )
                        }
                }
            }


        return gameMetadataDto ?: throw NotFoundException("$gameId not found")
    }

    suspend fun fetchMoves(gameId: GameId): GameMovesResponse {
        val moves =
            when (gameId.type) {
                DB -> referenceGameDaoService.listMoves(gameId.id)
                PVP -> pvpGameDaoService.listMoves(gameId.id)
                PVB -> pvbGameDaoService.listMoves(gameId.id)
            }

        return GameMovesResponse(moves)
    }

    suspend fun startGameAnalysis(gameId: GameId): StartGameAnalysisResponse {
        fun stillInProgressException(gameId: GameId, gameEventType: GameEventType) =
            BadRequestException("Game $gameId is in status $gameEventType and can not be analyzed yet")

        suspend fun validateAnalysisShouldStart(gameId: GameId): Pair<AnalysisStatus, Boolean> {
            val status = fetchAnalysisStatusOfGame(gameId)
            val shouldStart = (status == NOT_STARTED || status == PARTIALLY_COMPLETED) && appConfig.isEnginePoolEnabled

            if (shouldStart) {
                when (gameId.type) {
                    PVP -> {
                        val gameRecord = pvpGameDaoService.fetchById(gameId.id)
                            ?: throw NotFoundException("Game $gameId not found")

                        if (gameRecord.variant == Variant.MANCHU) {
                            throw BadRequestException("Analysis is not supported for Manchu variant games")
                        }

                        if (gameRecord.gameStatus.isInProgress()) {
                            throw stillInProgressException(gameId, gameRecord.gameStatus)
                        }
                    }

                    PVB -> {
                        val statusRecord = pvbGameDaoService.fetchGameStatus(gameId.id)
                            ?: throw NotFoundException("Game $gameId not found")

                        val pvbRecord = pvbGameDaoService.fetchById(gameId.id)
                        if (pvbRecord != null && (pvbRecord.variant == Variant.MANCHU)) {
                            throw BadRequestException("Analysis is not supported for Manchu variant games")
                        }

                        if (statusRecord.isInProgress()) {
                            throw stillInProgressException(gameId, statusRecord.status)
                        }
                    }

                    else -> {
                        // nothing to do
                    }
                }

                return status to true
            } else {
                return status to false
            }
        }

        val (status, shouldStartAnalysis) = validateAnalysisShouldStart(gameId)
        if (shouldStartAnalysis) {
            startAnalysisAsync(gameId)
        }
        return StartGameAnalysisResponse(status, shouldStartAnalysis)
    }

    suspend fun fetchAnalysisStatus(gameId: GameId): GameAnalysisStatusResponse {
        suspend fun calculateProgress(multiplicator: Int): Float {
            val total = countTotalMoves(gameId) * multiplicator
            return if (total > 0) {
                val countAnalyzed = moveAnalysisDaoService.countAnalyzedMoves(gameId)
                countAnalyzed.toFloat() / total.toFloat()
            } else {
                0f
            }
        }

        val status = fetchAnalysisStatusOfGame(gameId)

        val progress =
            when (status) {
                COMPLETED -> 1f
                NOT_STARTED -> 0f
                PARTIALLY_COMPLETED -> calculateProgress(1)
                STARTED, CANCELLED -> calculateProgress(2)
            }

        return GameAnalysisStatusResponse(status, progress)
    }

    suspend fun fetchAnalysisData(gameId: GameId): GameAnalysisResponse {
        val startFenResult = engineCacheService.get(DEFAULT_START_FEN)
        val start =
            if (startFenResult != null) {
                Pair(DEFAULT_START_FEN, startFenResult)
            } else {
                null
            }

        val preCached = moveAnalysisDaoService
            .listCachedAnalyzedMoves(gameId)
            .map { fenKey ->
                fenKey to engineCacheService.get(fenKey)
            }
            .filter { (_, value) -> value != null }
            .map { (fenKey, infoLineResult) ->
                fenKey to infoLineResult!!
            }

        val fromMoveAnalysisTable = moveAnalysisDaoService
            .listNonCachedAnalyzedMoves(gameId)
            .map { record ->
                record.fenKey to parseInfoLine(record.rawLine)
            }

        val entries =
            (listOfNotNull(start) + preCached + fromMoveAnalysisTable)
                .map { (fenKey, infoLineResult) ->
                    mapToInfoLineResultDto(fenKey, infoLineResult)
                }

        return GameAnalysisResponse(entries)
    }

    suspend fun listPreAnalysisToDelete(limit: Duration): List<Pair<GameId, Instant>> {
        val gameIds = mutableListOf<Pair<GameId, Instant>>()

        gameIds += pvpGameDaoService
            .listPreAnalysisToDelete(limit)
            .map { (id, startTime) -> Pair(GameId(PVP, id), startTime) }

        gameIds += pvbGameDaoService
            .listPreAnalysisToDelete(limit)
            .map { (id, startTime) -> Pair(GameId(PVB, id), startTime) }

        gameIds += referenceGameDaoService
            .listPreAnalysisToDelete(limit)
            .map { (id, startTime) -> Pair(GameId(DB, id), startTime) }

        return gameIds.toList()
    }

    suspend fun resetAnalysis(gameId: GameId) {
        moveAnalysisDaoService.deleteAnalysisData(gameId)
        moveAnalysisDaoService.resetAnalysisStatus(gameId)
    }

    private fun startAnalysisAsync(gameId: GameId) {
        suspend fun queryEngine(fen: String): InfoLineResult? {
            return enginesPool.safeQueryForDepth(
                fen = fen,
                engineId = PikafishEngineId,
                depth = DEFAULT_ANALYSIS_DEPTH,
                timeout = 20_000L
            )?.deepestResult()
        }

        suspend fun processPosition(positionIndex: Int, fen: String, isEngineMoveAnalysis: Boolean): MoveAnalysis {
            val fenKey = resetFullMoveCount(fen)
            val cachedInfoLineResult = engineCacheService.get(fenKey)

            val moveAnalysis = MoveAnalysis()

            if (cachedInfoLineResult != null) {
                moveAnalysis.alreadyInCache = true
            } else {
                moveAnalysis.alreadyInCache = false
                val infoLineResult = queryEngine(fen)
                moveAnalysis.rawLine = infoLineResult?.line
                moveAnalysis.depth = infoLineResult?.depth
            }

            if (moveAnalysis.alreadyInCache || (moveAnalysis.rawLine != null && moveAnalysis.depth != null)) {
                moveAnalysis.setGameId(gameId)
                moveAnalysis.fenKey = fenKey
                moveAnalysis.position = positionIndex
                moveAnalysis.isEngineMoveAnalysis = isEngineMoveAnalysis

                moveAnalysisDaoService.save(moveAnalysis)
            }

            return moveAnalysis
        }

        suspend fun processWhatTheEngineWouldHavePlayed(
            startFen: String,
            moves: List<String>
        ) {
            val infoLineResultsDtos = fetchAnalysisData(gameId).entries

            val board = Board(startFen)
            moves.forEachIndexed { positionIndex, uci ->
                val fenKeyBeforePlayedMove = resetFullMoveCount(board.outputFen())
                val bestMove =
                    infoLineResultsDtos.find { it.fen == fenKeyBeforePlayedMove && it.line != null }?.bestMove

                if (bestMove != null && bestMove != uci) {
                    val copy = board.copy()
                    copy.registerMove(bestMove)
                    val fenResultingFromEngineBestMove = copy.outputFen()
                    processPosition(
                        positionIndex = positionIndex,
                        fen = fenResultingFromEngineBestMove,
                        isEngineMoveAnalysis = true
                    )
                }

                board.registerMove(uci)
            }
        }

        analysisScope.launch {
            if (appConfig.isEnginePoolEnabled) {
                when (val analysisStatus = fetchAnalysisStatusOfGame(gameId)) {
                    PARTIALLY_COMPLETED -> {
                        moveAnalysisDaoService.startAnalysis(gameId)
                        logger.info { "$gameId completing analysis" }

                        try {
                            // at this stage, we only have the positions resulting from actually played moves analyzed,
                            // so we also need to add analysis of positions if engine had played its best move
                            val startFen = findStartFen(gameId)
                            val moves = findMoves(gameId)
                            processWhatTheEngineWouldHavePlayed(startFen, moves)
                            moveAnalysisDaoService.completeAnalysis(gameId)
                        } catch (e: Exception) {
                            logger.error(e) { "$gameId error while completing analysis" }
                            moveAnalysisDaoService.cancelAnalysis(gameId)
                        }
                    }

                    NOT_STARTED -> {
                        moveAnalysisDaoService.startAnalysis(gameId)
                        logger.info { "$gameId starting analysis" }

                        try {
                            val startFen = findStartFen(gameId)
                            val board = Board(startFen)
                            val moves = findMoves(gameId)

                            // analyze actually played moves
                            moves.forEachIndexed { positionIndex, uci ->
                                board.registerMove(uci)
                                processPosition(positionIndex, board.outputFen(), isEngineMoveAnalysis = false)
                            }

                            // analyze position in case the engine played its best move instead
                            processWhatTheEngineWouldHavePlayed(startFen, moves)

                            moveAnalysisDaoService.completeAnalysis(gameId)
                        } catch (e: Exception) {
                            logger.error(e) { "$gameId error while analyzing game" }
                            moveAnalysisDaoService.cancelAnalysis(gameId)
                        }
                    }

                    else -> {
                        logger.info { "$gameId do nothing for status $analysisStatus" }
                    }
                }
            }
        }
    }

    private suspend fun countTotalMoves(gameId: GameId): Int =
        when (gameId.type) {
            PVP -> pvpGameDaoService.countTotalMoves(gameId.id)
            PVB -> pvbGameDaoService.countTotalMoves(gameId.id)
            DB -> referenceGameDaoService.countTotalMoves(gameId.id) ?: 0
        }

    private suspend fun findMoves(gameId: GameId) =
        when (gameId.type) {
            PVP -> pvpGameDaoService.listMoves(gameId.id)
            PVB -> pvbGameDaoService.listMoves(gameId.id)
            DB -> referenceGameDaoService.listMoves(gameId.id)
        }

    private suspend fun findStartFen(gameId: GameId) =
        when (gameId.type) {
            PVP -> DEFAULT_START_FEN
            PVB -> pvbGameDaoService.fetchStartFen(gameId.id) ?: DEFAULT_START_FEN
            DB -> DEFAULT_START_FEN
        }

    private suspend fun fetchUserName(userId: String?): String? =
        userId?.let { userCache.fetchUsername(it) }

    suspend fun listLastPvpGames(
        requestedLimit: Int,
        distinctByUsers: Boolean,
        beforeTs: Long?
    ): ListLastGamesResponse {
        // ensure each user appears at most once in the list
        // maybe we could also make a distinct by user session or countries to avoid showing only sock-puppeteers
        fun distinctByUserId(games: List<Game>): List<Game> {
            val gamesByUniqueUserId = mutableListOf<Game>()
            var i = 0
            while (gamesByUniqueUserId.size < requestedLimit && i < games.size) {
                val game = games[i]
                val alreadyMentionedUserIds =
                    gamesByUniqueUserId.flatMap { listOf(it.invitee, it.inviter) }.filterNotNull()
                if (!alreadyMentionedUserIds.contains(game.inviter) && !alreadyMentionedUserIds.contains(game.invitee)) {
                    gamesByUniqueUserId.add(game)
                }
                i++
            }

            return gamesByUniqueUserId
        }

        val actualLimit = if (distinctByUsers) requestedLimit * 20 else requestedLimit
        val games = pvpGameDaoService
            .listLastGames(
                limit = actualLimit,
                minMoveIndex = MIN_MOVE_INDEX,
                beforeTs = beforeTs
            )

        val userIds = games.flatMap { game -> listOf(game.inviter, game.invitee) }.distinct().filterNotNull()
        val onlineUserIds = userService.areOnline(userIds).onlineUserIds
        val selectedGames = if (distinctByUsers) distinctByUserId(games) else games

        return selectedGames
            .take(actualLimit)
            .map { mapPlayerVsPlayerGameToDto(it, onlineUserIds) }
            .let { entries ->
                ListLastGamesResponse(entries)
            }
    }

    suspend fun listLastPvpGamesByUsername(
        username: String,
        requestedLimit: Int,
        beforeTs: Long?
    ): ListLastGamesResponse {
        val user = userDaoService.findByUserName(username)
            ?: throw NotFoundException("User $username could not be found")
        return listLastPvpGamesByUserId(
            userId = user.id,
            requestedLimit = requestedLimit,
            beforeTs = beforeTs
        )
    }

    suspend fun listLastPvpGamesByUserId(
        userId: String,
        requestedLimit: Int,
        beforeTs: Long?
    ): ListLastGamesResponse {
        val games = pvpGameDaoService.listLastGamesByUserId(
            userId = userId,
            limit = requestedLimit,
            minMoveIndex = MIN_MOVE_INDEX,
            beforeTs = beforeTs
        )

        val userIds = games.flatMap { game -> listOf(game.inviter, game.invitee) }.distinct().filterNotNull()
        val onlineUserIds = userService.areOnline(userIds).onlineUserIds

        return games
            .map { mapPlayerVsPlayerGameToDto(it, onlineUserIds) }
            .let { entries ->
                ListLastGamesResponse(entries)
            }
    }

    suspend fun listLatestPvbGames(
        requestedLimit: Int,
        distinctByUsers: Boolean = true,
        beforeTs: Long? = null,
        excludeAutoResigned: Boolean
    ): ListLastGamesResponse {
        val gameRecords = pvbGameDaoService
            .listLatestGamesByIdentifiedUsers(
                limit = requestedLimit,
                minMoveIndex = MIN_MOVE_INDEX,
                beforeTs = beforeTs,
                excludeAutoResigned = excludeAutoResigned,
                distinctByUsers = distinctByUsers,
                variantsToInclude = Variant.entries
            )

        val userIds = gameRecords.map { game -> game.userId }.distinct().filterNotNull()
        val onlineUserIds = userService.areOnline(userIds).onlineUserIds

        return gameRecords
            .take(requestedLimit)
            .map { record -> mapPlayerVsBotGameToDto(record, onlineUserIds) }
            .let { entries -> ListLastGamesResponse(entries) }
    }

    suspend fun listLastDbGamesByPlayerName(
        requestedLimit: Int,
        canonicalPlayerName: String,
        offset: Int?
    ): ListLastGamesResponse {
        val referencePlayer = referencePlayerDaoService.findPlayerByCanonicalName(canonicalPlayerName)
            ?: throw NotFoundException("Player '$canonicalPlayerName' not found")

        val games = referenceGameDaoService.search(
            playerIds = listOf(referencePlayer.id),
            limit = requestedLimit,
            offset = offset
        )

        return ListLastGamesResponse(mapDatabaseGamesToDto(games, offset))
    }

    suspend fun listLastDbGamesByEventId(
        requestedLimit: Int,
        eventId: String,
        round: Int?,
        offset: Int?
    ): ListLastGamesResponse {
        val games = referenceGameDaoService.search(
            eventIds = listOf(eventId),
            round = round,
            limit = requestedLimit,
            offset = offset
        )

        return ListLastGamesResponse(mapDatabaseGamesToDto(games, offset))
    }

    suspend fun fetchLatestGamesUpdate(request: LatestGamesUpdateRequest): LatestGamesUpdateResponse {
        val idsByType = request
            .gameIds
            .groupBy({ gameID -> gameID.type }, { gameId -> gameId.id })

        val pvpGames = pvpGameDaoService.fetchCurrentStatusAndFen(idsByType[PVP].orEmpty())
        val pvbGames = pvbGameDaoService.fetchCurrentStatusAndFen(idsByType[PVB].orEmpty())

        val entries = pvpGames.map { game ->
            LatestGamesUpdateResponse.Entry(
                gameId = GameId(PVP, game.id),
                status = game.gameStatus,
                fen = game.currentFen,
                lastUpdated = game.lastUpdated.toEpochMilliseconds()
            )
        } + pvbGames.map { game ->
            LatestGamesUpdateResponse.Entry(
                gameId = GameId(PVB, game.id),
                status = game.gameStatus,
                fen = game.currentFen,
                lastUpdated = game.lastUpdated.toEpochMilliseconds()
            )
        }

        return LatestGamesUpdateResponse(entries)
    }

    private suspend fun mapPlayerVsBotGameToDto(record: BotGame, onlineUserIds: Set<String>): GameMetadataDto {
        val redUserId: String?
        val redPlayerName: String?
        val redPlayerRating: Int?
        val redUserType: UserType?
        val blackUserId: String?
        val blackPlayerName: String?
        val blackPlayerRating: Int?
        val blackUserType: UserType?
        val isUserOnline = onlineUserIds.contains(record.userId)

        if (record.userColor == RED) {
            redUserId = record.userId
            redPlayerName = fetchUserName(record.userId)
            redPlayerRating = null
            redUserType = userCache.fetchUserType(record.userId)
            blackUserId = null
            blackPlayerName = record.prettyEngineName()
            blackPlayerRating = null
            blackUserType = null
        } else {
            redUserId = null
            redPlayerName = record.prettyEngineName()
            redPlayerRating = null
            redUserType = null
            blackUserId = record.userId
            blackPlayerName = fetchUserName(record.userId)
            blackPlayerRating = null
            blackUserType = userCache.fetchUserType(record.userId)
        }

        return GameMetadataDto(
            gameId = GameId(PVB, record.id),
            redPlayerId = redUserId,
            redPlayerName = redPlayerName,
            redPlayerRating = redPlayerRating,
            redUserType = redUserType,
            blackPlayerId = blackUserId,
            blackPlayerName = blackPlayerName,
            blackPlayerRating = blackPlayerRating,
            blackUserType = blackUserType,
            isRedOnline = isUserOnline,
            isBlackOnline = isUserOnline,
            userColor = record.userColor,
            finalFen = record.currentFen,
            status = record.gameStatus,
            outcome = record.outcome,
            lastUpdated = record.lastUpdated.toEpochMilliseconds(),
            variant = record.variant,
        )

    }

    private suspend fun mapPlayerVsPlayerGameToDto(gameRecord: Game, onlineUserIds: Set<String>): GameMetadataDto {
        val redUserId = gameRecord.redUserId()!!
        val blackUserId = gameRecord.blackUserId()!!
        return GameMetadataDto(
            gameId = GameId(PVP, gameRecord.id),
            redPlayerId = redUserId,
            redPlayerName = fetchUserName(redUserId),
            redPlayerRating = gameRecord.redPlayerRating(),
            redUserType = userCache.fetchUserType(redUserId),
            isRedOnline = onlineUserIds.contains(redUserId),
            blackPlayerId = blackUserId,
            blackPlayerName = fetchUserName(blackUserId),
            blackPlayerRating = gameRecord.blackPlayerRating(),
            blackUserType = userCache.fetchUserType(blackUserId),
            isBlackOnline = onlineUserIds.contains(blackUserId),
            userColor = null,
            finalFen = gameRecord.currentFen,
            status = gameRecord.gameStatus,
            outcome = gameRecord.outcome,
            lastUpdated = gameRecord.lastUpdated.toEpochMilliseconds(),
            variant = gameRecord.variant
        )
    }

    private suspend fun mapDatabaseGamesToDto(games: List<ReferenceGame>, offset: Int?): List<GameMetadataDto> {
        return games.mapIndexed { i, game ->
            GameMetadataDto(
                gameId = GameId(DB, game.id),
                redPlayerId = game.redPlayer,
                redPlayerName = databaseService.playerIdToCanonicalName(game.redPlayer),
                blackPlayerId = game.blackPlayer,
                blackPlayerName = databaseService.playerIdToCanonicalName(game.blackPlayer),
                finalFen = game.finalFen ?: DEFAULT_START_FEN,
                outcome = game.outcome,
                lastUpdated = game.date?.atStartOfDay()?.toUtcInstant()?.toEpochMilliseconds(),
                paginationOffset = (offset ?: 0) + i,
                variant = Variant.XIANGQI
            )
        }
    }

    companion object {

        const val DEFAULT_ANALYSIS_DEPTH = 20
        const val MIN_MOVE_INDEX = 6

    }

}
