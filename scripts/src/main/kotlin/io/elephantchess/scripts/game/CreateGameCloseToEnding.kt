package io.elephantchess.scripts.game

import io.elephantchess.db.dao.codegen.Tables.REFERENCE_GAME
import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.model.TimeControlMode
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.scripts.KoinScriptInit
import io.elephantchess.scripts.game.Utils.createTestUserIfNotExists
import io.elephantchess.servicelayer.dto.game.CreateGameRequest
import io.elephantchess.servicelayer.dto.game.JoinGameRequest
import io.elephantchess.servicelayer.dto.game.PlayMoveRequest
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.PlayerVsPlayerGameService
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.HalfMove
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes

object CreateGameCloseToEnding : KoinScriptInit() {

    private val dslContext by inject<DSLContext>()
    private val pvpGameService by inject<PlayerVsPlayerGameService>()
    private val userDaoService by inject<UserDaoService>()
    private val referenceGameDaoService by inject<ReferenceGameDaoService>()

    private suspend fun listAllReferenceGameIds(): List<String> {
        return dslContext
            .select(REFERENCE_GAME.ID)
            .from(REFERENCE_GAME)
            .awaitMappedRecords()
    }

    private suspend fun fetchMoves(referenceGameId: String): List<HalfMove> {
        return referenceGameDaoService
            .listMoves(referenceGameId)
            .map { uci -> HalfMove.parseMoveFromUci(uci) }
    }

    private suspend fun endsInCheckmate(gameId: String): Boolean {
        val board = Board()
        board.registerMoves(fetchMoves(gameId))
        return board.isCheckmated()
    }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val instances = 1

        // set up users
        createTestUserIfNotExists(4, randomizeRatings = false)
        val user1 = userDaoService.findByUserName("test4")!!
        val user2 = userDaoService.findByUserName("benckx")!!

        // create games
        repeat(instances) {
            // find reference game
            val gameIds = listAllReferenceGameIds()
            println("found ${gameIds.size} games")
            val referenceGameId = gameIds.shuffled().find { gameId -> endsInCheckmate(gameId) }!!
            println("found game that ends in checkmate: $referenceGameId")

            val request =
                CreateGameRequest(
                    inviterColor = Color.RED,
                    isRated = true,
                    timeControlBase = 30.minutes.inWholeSeconds.toInt(),
                    timeControlIncrement = null,
                    timeControlMode = TimeControlMode.GAME_TIME,
                    allowGuests = true,
                    alwaysVisibleInLobby = false,
                    privateInvite = false,
                )

            val response = pvpGameService.createGame(UserId(AUTHENTICATED, user1.id), request)
            val gameId = response.gameId
            println("created game $gameId")
            pvpGameService.joinGame(UserId(AUTHENTICATED, user2.id), JoinGameRequest(gameId))

            // play the moves
            val movesToRemove = 1
            val moves = fetchMoves(referenceGameId)

            moves
                .dropLast(movesToRemove)
                .forEachIndexed { i, move ->
                    val userId = if (i % 2 == 0) user1.id else user2.id
                    pvpGameService.playMove(userId, PlayMoveRequest(gameId, move.toUci()))
                }

            println(
                "last $movesToRemove move(s): ${
                    moves.takeLast(movesToRemove).joinToString(", ") { it.toAlgebraic() }
                }"
            )

            exitProcess(0)
        }
    }

}
