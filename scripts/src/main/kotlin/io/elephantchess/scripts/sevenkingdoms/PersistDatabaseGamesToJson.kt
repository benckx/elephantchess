package io.elephantchess.scripts.sevenkingdoms

import com.google.common.io.Files
import io.elechantchess.sevenkingdoms.testutils.GameEntryCacheManager.gameEntriesToJson
import io.elechantchess.sevenkingdoms.testutils.GameEntryDto
import io.elephantchess.db.dao.codegen.Tables.SEVEN_KINGDOMS_GAME
import io.elephantchess.db.dao.codegen.tables.pojos.SevenKingdomsGame
import io.elephantchess.db.services.SevenKingdomsGameDaoService
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.model.GameEventType.OTHER_VICTORY
import io.elephantchess.scripts.KoinScript
import io.elephantchess.sevenkingdoms.Board
import io.elephantchess.sevenkingdoms.VictoryType
import io.elephantchess.sevenkingdoms.VictoryType.CAPTURED_ENOUGH_KINGDOMS
import io.elephantchess.sevenkingdoms.VictoryType.CAPTURED_ENOUGH_PIECES
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.koin.core.component.inject
import java.io.File
import java.nio.charset.Charset

object PersistDatabaseGamesToJson : KoinScript {

    init {
        initKoin(appProfile = "local")
    }

    private const val GAMES_TO_KEEP = 200

    private val gameDaoService by inject<SevenKingdomsGameDaoService>()
    private val dslContext by inject<DSLContext>()

    private suspend fun listGamesSortedBySize(): List<SevenKingdomsGame> {
        return dslContext
            .select(
                SEVEN_KINGDOMS_GAME.ID,
                SEVEN_KINGDOMS_GAME.CURRENT_INDEX
            )
            .from(SEVEN_KINGDOMS_GAME)
            .where(SEVEN_KINGDOMS_GAME.GAME_STATUS.eq(OTHER_VICTORY))
            .orderBy(SEVEN_KINGDOMS_GAME.CURRENT_INDEX)
            .awaitMappedRecords()
    }

    private fun mapBoardToGameEntryDto(gameId: String, board: Board): GameEntryDto {
        return GameEntryDto(
            gameId = gameId,
            winner = board.winner()!!,
            victoryType = board.victoryType()!!,
            capturedKingdoms = board.capturedKingdomsMap(),
            colorsStillInGame = board.colorsStillInGame(),
            moves = board.listHistoricalMoves().map { it.uci }
        )
    }

    private suspend fun loadBoard(gameId: String): Board {
        val board = Board()
        board.registerMoves(gameDaoService.fetchMovesUci(gameId))
        return board
    }

    private suspend fun findGamesWithThresholdElimination(gameIds: List<String>): List<GameEntryDto> {
        val result = mutableListOf<GameEntryDto>()
        var i = 0

        while (result.size < GAMES_TO_KEEP && i < gameIds.size) {
            val gameId = gameIds[i++]
            val board = loadBoard(gameId)
            if (board.listCaptureEvents().any { !it.generalCapture }) {
                result.add(mapBoardToGameEntryDto(gameId, board))
            }
        }

        return result.toList()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            val gamesByVictoryType = mutableMapOf<VictoryType, MutableList<String>>()
            val games = listGamesSortedBySize()

            VictoryType.entries.forEach { victoryType ->
                gamesByVictoryType[victoryType] = mutableListOf()
            }

            fun isBelowLimit(victoryType: VictoryType): Boolean =
                (gamesByVictoryType[victoryType]?.size ?: 0) < GAMES_TO_KEEP

            var i = 0
            while (
                i < games.size &&
                (isBelowLimit(CAPTURED_ENOUGH_KINGDOMS) || isBelowLimit(CAPTURED_ENOUGH_PIECES))
            ) {
                val board = loadBoard(games[i++].id)
                val victoryType = board.victoryType()!!
                if (isBelowLimit(victoryType))
                    gamesByVictoryType[victoryType]!! += games[i].id
            }

            println("total games from DB: ${games.size}")
            gamesByVictoryType.forEach { (victoryType, gameIds) ->
                println("$victoryType -> ${gameIds.size}")
            }

            val gameDtosByVictorType =
                gamesByVictoryType.values.flatten().map { gameId ->
                    val board = loadBoard(gameId)
                    mapBoardToGameEntryDto(gameId, board)
                }

            val gameDtoByThresholdElimination = findGamesWithThresholdElimination(games.map { it.id })
            val allGameToSave = gameDtosByVictorType + gameDtoByThresholdElimination

            println("games by victory type: ${gameDtosByVictorType.size}")
            println("games by threshold elimination: ${gameDtoByThresholdElimination.size}")
            println("total games to save: ${allGameToSave.size}")

            // output the shortest games to JSON
            val json = gameEntriesToJson(allGameToSave)
            val jsonFile = File("seven-kingdoms-core-test-utils/src/main/resources/games.json")
            val charset = Charset.forName("UTF-8")
            Files.asCharSink(jsonFile, charset).write(json)
        }
    }
}
