package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.tables.pojos.SevenKingdomsGame
import io.elephantchess.db.services.SevenKingdomsGameDaoService
import io.elephantchess.db.utils.*
import io.elephantchess.model.GameEventType.*
import io.elephantchess.servicelayer.dto.sevenkingdoms.*
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.exceptions.NotFoundException
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.sevenkingdoms.Board
import io.elephantchess.sevenkingdoms.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.sevenkingdoms.Color
import io.elephantchess.sevenkingdoms.Color.*
import io.elephantchess.sevenkingdoms.Color.Companion.areContiguous
import io.elephantchess.sevenkingdoms.ExtraEliminationEvent
import kotlin.time.Clock

class SevenKingdomsGameService(
    private val gameDaoService: SevenKingdomsGameDaoService,
    private val userCache: UserCache
) {

    suspend fun createGame(request: CreateGameRequest): CreateGameResponse {
        if (request.minPlayers !in 2..7)
            throw BadRequestException("minPlayers must be [2-7], ${request.minPlayers} is invalid")

        if (request.minPlayers !in acceptedNumbersOfMinPlayers)
            throw BadRequestException("minPlayers must be one of $acceptedNumbersOfMinPlayers")

        val existingGame = gameDaoService.countGamesWithStatusCreated(request.minPlayers)
        if (existingGame > 0)
            throw BadRequestException("A similar open game already exists")

        val now = Clock.System.now()
        val gameRecord = SevenKingdomsGame()
        gameRecord.id = generateId()
        gameRecord.currentIndex = 0
        gameRecord.currentFen = DEFAULT_START_FEN
        gameRecord.colorToPlay = WHITE
        gameRecord.created = now
        gameRecord.lastUpdated = now
        gameRecord.minPlayers = request.minPlayers
        gameRecord.gameStatus = CREATED
        gameDaoService.save(gameRecord)

        return CreateGameResponse(gameRecord.id)
    }

    suspend fun fetchGameData(gameId: String): GetGameDataResponse {
        val gameRecord = gameDaoService.fetchGame(gameId)
            ?: throw NotFoundException("Game $gameId not found")

        suspend fun mapToPlayer(color: Color): GetGameDataResponse.Player? =
            gameRecord.userIdOfColor(color)?.let { userId ->
                val userName = userCache.fetchUsernameOrDefault(userId)
                GetGameDataResponse.Player(userId, userName)
            }

        suspend fun mapWinnerToPlayer(): GetGameDataResponse.Player? =
            gameRecord.winnerUserId?.let { userId ->
                val userName = userCache.fetchUsernameOrDefault(userId)
                GetGameDataResponse.Player(userId, userName)
            }

        return GetGameDataResponse(
            whitePlayer = mapToPlayer(WHITE),
            redPlayer = mapToPlayer(RED),
            orangePlayer = mapToPlayer(ORANGE),
            bluePlayer = mapToPlayer(BLUE),
            greenPlayer = mapToPlayer(GREEN),
            purplePlayer = mapToPlayer(PURPLE),
            blackPlayer = mapToPlayer(BLACK),
            winner = mapWinnerToPlayer(),
            status = gameRecord.gameStatus,
            currentIndex = gameRecord.currentIndex,
            currentFen = gameRecord.currentFen,
            colorToPlay = gameRecord.colorToPlay
        )
    }

    suspend fun fetchMoves(gameId: String): GetMovesResponse {
        return GetMovesResponse(gameDaoService.fetchMovesUci(gameId))
    }

    suspend fun joinGame(userId: UserId, request: JoinGameRequest): JoinGameResponse {
        // TODO: check user isn't guest and email is validated

        if (request.colors.isEmpty())
            throw BadRequestException("Colors must not be empty")

        if (request.colors.size > 1 && !areContiguous(request.colors))
            throw BadRequestException("Colors are not contiguous ${request.colors.joinToString(", ")}")

        val gameRecord = gameDaoService.fetchGame(request.gameId)
            ?: throw NotFoundException("Game ${request.gameId} not found")

        if (request.colors.size > gameRecord.maxColorPerPlayer())
            throw BadRequestException("You can only select up to ${gameRecord.maxColorPerPlayer()} color(s) for this game")

        if (gameRecord.gameStatus != CREATED)
            throw BadRequestException("Game ${request.gameId} is not open to join")

        request.colors.forEach { color ->
            if (gameRecord.userIdOfColor(color) != null && gameRecord.userIdOfColor(color) != userId.id)
                throw BadRequestException("Color $color is already taken")
        }

        // gather all colors player now plays with
        val colorsOfUser = (request.colors + gameRecord.colorsOfUser(userId.id)).distinct().sorted()

        if (colorsOfUser.size > gameRecord.maxColorPerPlayer())
            throw BadRequestException("You can only select up to ${gameRecord.maxColorPerPlayer()} color(s) for this game")

        // update status to JOINED if all colors are now taken
        val oldFreeColors = gameRecord.freeColors().toSet()
        val newFreeColors = oldFreeColors.minus(colorsOfUser.toSet())
        val newStatus =
            if (newFreeColors.isEmpty()) {
                JOINED
            } else {
                CREATED
            }

        // TODO: if game status is JOINED -> send mail to other users

        // persist
        gameDaoService.updateJoinStatus(
            userId = userId.id,
            gameId = request.gameId,
            newColorsOfUser = colorsOfUser,
            newStatus = newStatus
        )

        return JoinGameResponse(colorsOfUser, newStatus)
    }

    suspend fun cancelJoinGame(userId: UserId, request: CancelJoinGameRequest): CancelJoinGameResponse {
        val gameRecord = gameDaoService.fetchGame(request.gameId)
            ?: throw NotFoundException("Game ${request.gameId} not found")

        if (gameRecord.gameStatus != CREATED)
            throw BadRequestException("Game ${request.gameId} is not open to join")

        val currentColors = gameRecord.colorsOfUser(userId.id)
        val newColors = currentColors.minus(request.colors.toSet())

        if (newColors.size > 1 && !areContiguous(newColors))
            throw BadRequestException("Colors are not contiguous ${newColors.joinToString(", ")}")

        // persist
        gameDaoService.updateJoinStatus(
            userId = userId.id,
            gameId = request.gameId,
            newColorsOfUser = newColors,
            newStatus = CREATED
        )

        return CancelJoinGameResponse(newColors)
    }

    suspend fun resign(userId: UserId, request: ResignRequest) {
        val gameRecord = gameDaoService.fetchGame(request.gameId)
            ?: throw NotFoundException("Game ${request.gameId} not found")

        if (gameRecord.gameStatus != JOINED)
            throw BadRequestException("Game ${request.gameId} hasn't start yet")

        val colorsOfUser = gameRecord.colorsOfUser(userId.id)
        if (colorsOfUser.isEmpty())
            throw BadRequestException("User ${userId.id} is not part of game ${request.gameId}")

        val board = loadBoard(request.gameId)
        val allEliminations = board.allEliminatedColors().toMutableList()
        allEliminations.forEach { color ->
            if (color in colorsOfUser) {
                throw BadRequestException("User ${userId.id} is already eliminated")
            }
        }

        // if only one player is still playing, they win
        allEliminations += colorsOfUser

        val remainingPairs =
            gameRecord
                .colorToUserIdMap()
                .filter { (_, userId) -> userId != null }
                .map { (color, userId) -> color to userId!! }
                .filter { (color, _) -> !allEliminations.contains(color) }
                .toList()

        val remainingUserIds = remainingPairs.map { it.second }.distinct()
        val newStatus = if (remainingUserIds.size == 1) OTHER_VICTORY else null

        // persist
        gameDaoService.resign(
            gameId = request.gameId,
            index = board.currentIndex,
            userId = userId.id,
            colors = colorsOfUser,
            newStatus = newStatus,
            winnerUserId = remainingPairs.firstOrNull()?.second
        )
    }

    suspend fun playMove(userId: UserId, request: PlayMoveRequest): PlayMoveResponse {
        val gameRecord = gameDaoService.fetchGame(request.gameId)
            ?: throw NotFoundException("Game ${request.gameId} not found")

        if (gameRecord.gameStatus != JOINED)
            throw BadRequestException("Game ${request.gameId} is not open to play")

        if (gameRecord.colorsOfUser(userId.id).isEmpty())
            throw BadRequestException("User ${userId.id} doesn't play in game ${request.gameId}")

        val board = loadBoard(request.gameId)

        if (board.allEliminatedColors().containsAll(gameRecord.colorsOfUser(userId.id)))
            throw BadRequestException("User ${userId.id} is already eliminated, it's ${board.colorToPlay()}'s turn")

        // should not be possible if status has been updated correctly (see above)
        if (board.winner() != null)
            throw BadRequestException("Game ${request.gameId} already has a winner")

        val colorToPlay = board.colorToPlay()
        if (userId.id != gameRecord.userIdOfColor(colorToPlay))
            throw BadRequestException("${userId.id} is not allowed to play color $colorToPlay")

        val numberOfEventBefore = board.listCaptureEvents().size

        try {
            board.registerMove(request.move)
        } catch (e: IllegalArgumentException) {
            throw BadRequestException("Invalid move ${request.move}: ${e.message}")
        }

        // win conditions
        val winner = board.winner()
        val statusUpdate = if (winner != null) OTHER_VICTORY else null
        val newColorToPlay = if (winner == null) board.colorToPlay() else null
        val newArmyCapturedEvents = board.listCaptureEvents()
        val armyCapturedEvent =
            if (newArmyCapturedEvents.size > numberOfEventBefore) {
                newArmyCapturedEvents.last()
            } else {
                null
            }

        // persist
        gameDaoService.playMove(
            gameId = request.gameId,
            userId = userId.id,
            move = request.move,
            newIndex = board.currentIndex,
            newFen = board.outputFen(),
            newColorToPlay = newColorToPlay,
            eventType = statusUpdate,
            winnerColor = board.winner(),
            winnerId = board.winner()?.let { gameRecord.userIdOfColor(it) }
        )

        return PlayMoveResponse(
            index = board.currentIndex,
            colorToPlay = newColorToPlay,
            statusUpdate = statusUpdate,
            winner = winner,
            victoryType = board.victoryType(),
            armyCapturedEvent = armyCapturedEvent
        )
    }

    private suspend fun loadBoard(gameId: String): Board {
        val eliminationEvents =
            gameDaoService
                .fetchEliminationEvents(gameId)
                .map { eventRecord ->
                    ExtraEliminationEvent(eventRecord.index, eventRecord.colors.map { color -> Color.valueOf(color) })
                }

        val board = Board()
        board.setExtraEliminationEvents(eliminationEvents)
        board.registerMoves(gameDaoService.fetchMovesUci(gameId))
        return board
    }

    companion object {

        val acceptedNumbersOfMinPlayers = listOf(2, 4, 7)

    }

}
