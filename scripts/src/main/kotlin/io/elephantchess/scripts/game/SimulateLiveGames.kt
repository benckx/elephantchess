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
import io.elephantchess.xiangqi.Color
import io.elephantchess.xiangqi.HalfMove
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * To test that on the home page (lobby), we can see the active games updated and animated on the thumb boards
 */
object SimulateLiveGames : KoinScriptInit() {

    private val dslContext by inject<DSLContext>()
    private val pvpGameService by inject<PlayerVsPlayerGameService>()
    private val userDaoService by inject<UserDaoService>()
    private val referenceGameDaoService by inject<ReferenceGameDaoService>()

    private const val NUMBER_OF_GAMES = 6
    private const val NUMBER_OF_USERS = 50

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

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        // set up users
        (1..NUMBER_OF_USERS).forEach { i -> createTestUserIfNotExists(i, randomizeRatings = false) }

        val users = (1..NUMBER_OF_USERS).map { i -> userDaoService.findByUserName("test$i")!! }

        val gameIds = listAllReferenceGameIds()
        println("found ${gameIds.size} reference games")

        // pair users two by two and run each game in parallel
        val jobs = users.chunked(2).take(NUMBER_OF_GAMES).mapIndexed { index, pair ->
            async {
                val user1 = pair[0]
                val user2 = pair[1]

                val referenceGameId = gameIds.random()
                val moves = fetchMoves(referenceGameId)
                println("[game #$index] using reference game $referenceGameId with ${moves.size} moves between ${user1.handle} and ${user2.handle}")

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
                println("[game #$index] created game $gameId")
                pvpGameService.joinGame(UserId(AUTHENTICATED, user2.id), JoinGameRequest(gameId))

                moves.forEachIndexed { i, move ->
                    val userId = if (i % 2 == 0) user1.id else user2.id
                    pvpGameService.playMove(userId, PlayMoveRequest(gameId, move.toUci()))
                    println("[game #$index] played move ${move.toUci()} on $gameId by ${if (i % 2 == 0) user1.handle else user2.handle}")
                    delay(1.seconds)
                }

                println("[game #$index] finished playing all moves on $gameId")
            }
        }

        jobs.awaitAll()
        exitProcess(0)
    }

}
