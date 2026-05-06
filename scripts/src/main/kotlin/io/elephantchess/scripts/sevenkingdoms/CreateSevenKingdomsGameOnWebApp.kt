package io.elephantchess.scripts.sevenkingdoms

import io.elechantchess.sevenkingdoms.testutils.GameEntryCacheManager.randomGame
import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.pojos.SevenKingdomsGame
import io.elephantchess.db.services.SevenKingdomsGameDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.maxColorPerPlayer
import io.elephantchess.db.utils.userIdOfColor
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.scripts.KoinScript
import io.elephantchess.scripts.game.Utils.createTestUserIfNotExists
import io.elephantchess.servicelayer.dto.sevenkingdoms.CreateGameRequest
import io.elephantchess.servicelayer.dto.sevenkingdoms.JoinGameRequest
import io.elephantchess.servicelayer.dto.sevenkingdoms.PlayMoveRequest
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.SevenKingdomsGameService
import io.elephantchess.sevenkingdoms.Color
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import kotlin.system.exitProcess

object CreateSevenKingdomsGameOnWebApp : KoinScript {

    private const val MOVE_TO_DROP = 0
    private const val TESTER = "benckx"

    private val dslContext by inject<DSLContext>()
    private val gameService by inject<SevenKingdomsGameService>()
    private val userDaoService by inject<UserDaoService>()
    private val gameDaoService by inject<SevenKingdomsGameDaoService>()

    private val userNames = listOf(TESTER) + (1..6).map { i -> "test$i" }

    init {
        initKoin()
    }

    private suspend fun deleteAll() {
        listOf(
            SEVEN_KINGDOMS_GAME_EVENT,
            SEVEN_KINGDOMS_GAME_MOVE,
            SEVEN_KINGDOMS_GAME
        ).forEach { table ->
            dslContext.deleteFrom(table).awaitExecute()
        }
    }

    // that includes the tester
    private fun randomListOfUserIds(gameRecord: SevenKingdomsGame): List<String> {
        var result = listOf<String>()
        while (!(result.isNotEmpty() && result.contains(TESTER) && result.indexOf(TESTER) <= (gameRecord.minPlayers - 1))) {
            result = userNames.shuffled()
        }
        return result
    }

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            deleteAll()

            (1..10).forEach { createTestUserIfNotExists(it) }
            var gameDto = randomGame()
//        while (!gameDto.toBoard().listCaptureEvents().any { !it.generalCapture }) {
//            println("retrying...")
//            gameDto = randomGame()
//        }

            val request = CreateGameRequest(2)
            val response = gameService.createGame(request)
            val gameId = response.gameId
            var gameRecord = gameDaoService.fetchGame(gameId)!!
            val users = randomListOfUserIds(gameRecord)

            println("users: $users")

            var i = 1
            Color.entries.chunked(gameRecord.maxColorPerPlayer()).forEach { colors ->
                val user = userDaoService.findByUserName(users[i++])!!
                gameService.joinGame(
                    UserId(AUTHENTICATED, user.id),
                    JoinGameRequest(gameId, colors)
                )
            }

            gameRecord = gameDaoService.fetchGame(gameId)!!
            var colorToPlay = Color.WHITE

            gameDto.moves.dropLast(MOVE_TO_DROP).forEach { move ->
                val playMoveRequest = PlayMoveRequest(gameId, move)
                val userId = gameRecord.userIdOfColor(colorToPlay)!!

                gameService
                    .playMove(UserId(AUTHENTICATED, userId), playMoveRequest)
                    .colorToPlay
                    ?.let { colorToPlay = it }
            }

            val board = gameDto.toBoard()
            board.listCaptureEvents().forEach { println(it) }
            println("captures: ${board.capturesCount()}")
            println("captures (summed): ${board.capturesCount().toList().sumOf { it.second }}")
            println("losses: ${board.lossesCount()}")
            println("losses (summed): ${board.lossesCount().toList().sumOf { it.second }}")
            println("captures (from history): ${board.listHistoricalMoves().count { it.capture != null }}")
            println("captures (from captures map): ${board.captures().values.flatten().size}")
            println("captured kingdoms: ${board.capturedKingdomsMap()}")
            println("winner: ${board.winner()}")
            println("victoryType: ${board.victoryType()}")

//        val colorCapturedByNumberOfPiecesRule = board.listCaptureEvents().find { !it.generalCapture }!!.capturedColor
            board.listHistoricalMoves().forEachIndexed { index, move ->
                // && move.capture!!.color == colorCapturedByNumberOfPiecesRule
                if (move.capture != null) {
                    print("[$index] $move")
                    if (move.armyCapturedEvent != null) {
                        println(" -> ${move.armyCapturedEvent}")
                    } else {
                        println()
                    }
                }
            }

            println("game created with id $gameId")
            println("http://localhost:8080/7k/game?gameId=$gameId")
        }
        exitProcess(0)
    }

}
