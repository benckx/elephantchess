package io.elephantchess.scripts.game

import io.elephantchess.db.services.ReferenceGameDaoService
import io.elephantchess.db.services.UserDaoService
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
import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.hours

object CreateGameWithPerpetualChecking : KoinScriptInit() {

    private val userDaoService by inject<UserDaoService>()
    private val referenceGameDaoService by inject<ReferenceGameDaoService>()
    private val pvpGameService by inject<PlayerVsPlayerGameService>()

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            // set up users
            createTestUserIfNotExists(4, randomizeRatings = false)
            val inviter = userDaoService.findByUserName("benckx")!!
            val invitee = userDaoService.findByUserName("test4")!!

            // fetch moves
            val referenceGameId = "WRX1whTF"
            val moves = referenceGameDaoService.listMoves(referenceGameId).map { parseMoveFromUci(it) }

            val board = Board(keepHistory = true)
            board.registerMoves(moves)
            val history = board.getHistory()!!

            listOf(RED, BLACK).forEach { color ->
                // detect sequences of consecutive checks
                val map = history.findSequencesOfConsecutiveChecks(color)
                if (map.isNotEmpty()) {
                    println("found sequence for $color")

                    map.toList().sortedBy { (key, _) -> key }.forEach { (key, sequence) ->
                        val fullMovesStr = sequence.fullMoves().joinToString(", ")
                        println("[$key] ${sequence.attackers} / $fullMovesStr")
                    }

                    val sequence = map.toList().last().second
                    val indexTo = sequence.moves.last().index

                    // create game
                    val request =
                        CreateGameRequest(
                            inviterColor = color.reverse(),
                            isRated = true,
                            timeControlBase = 1.hours.inWholeSeconds.toInt(),
                            timeControlIncrement = null,
                            timeControlMode = TimeControlMode.GAME_TIME,
                            allowGuests = true,
                            alwaysVisibleInLobby = false,
                            privateInvite = false
                        )

                    val response = pvpGameService.createGame(UserId(AUTHENTICATED, inviter.id), request)
                    val gameId = response.gameId
                    println("created game $gameId")
                    pvpGameService.joinGame(UserId(AUTHENTICATED, invitee.id), JoinGameRequest(gameId))

                    val movesToRemove = 1

                    moves
                        .subList(0, indexTo + 1 - movesToRemove)
                        .forEachIndexed { i, move ->
                            val userId = if (i % 2 == 0) invitee.id else inviter.id
                            pvpGameService.playMove(userId, PlayMoveRequest(gameId, move.toUci()))
                        }
                }
            }

            exitProcess(0)
        }
    }

}
