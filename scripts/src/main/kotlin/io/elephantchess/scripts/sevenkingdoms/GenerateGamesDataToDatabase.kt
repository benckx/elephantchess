package io.elephantchess.scripts.sevenkingdoms

import io.elephantchess.db.dao.codegen.tables.daos.SevenKingdomsGameDao
import io.elephantchess.db.dao.codegen.tables.daos.SevenKingdomsGameMoveDao
import io.elephantchess.db.dao.codegen.tables.pojos.SevenKingdomsGame
import io.elephantchess.db.dao.codegen.tables.pojos.SevenKingdomsGameMove
import io.elephantchess.db.utils.generateId
import io.elephantchess.db.utils.insertMultipleReactive
import io.elephantchess.db.utils.insertReactive
import io.elephantchess.model.GameEventType.OTHER_VICTORY
import io.elephantchess.scripts.KoinScript
import io.elephantchess.sevenkingdoms.Board
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.koin.core.component.inject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock

/**
 * Generate random games that can be used for testing
 */
object GenerateGamesDataToDatabase : KoinScript {

    private val logger = KotlinLogging.logger {}

    init {
        initKoin(appProfile = "local")
    }

    private val dslContext by inject<DSLContext>()

    private fun generateGame(): Board {
        val board = Board()
        while (!board.isGameOver) {
            val colorToPlay = board.colorToPlay()
            val moves = board.listLegalMovesFor(colorToPlay)
            board.registerMove(moves.random())
        }
        return board
    }

    private suspend fun persistGame(board: Board) {
        val now = Clock.System.now()
        val gameRecord = SevenKingdomsGame()
        gameRecord.id = generateId()
        gameRecord.currentIndex = board.listHistoricalMoves().size
        gameRecord.currentFen = board.outputFen()
        gameRecord.colorToPlay = null
        gameRecord.created = now
        gameRecord.lastUpdated = now
        gameRecord.minPlayers = 7
        gameRecord.gameStatus = OTHER_VICTORY
        gameRecord.winnerColor = board.winner()!!

        dslContext.transactionCoroutine { cfg ->
            val gameDao = SevenKingdomsGameDao(cfg)
            val moveDao = SevenKingdomsGameMoveDao(cfg)

            val movesRecords =
                board.listHistoricalMoves().mapIndexed { index, move ->
                    val moveRecord = SevenKingdomsGameMove()
                    moveRecord.gameId = gameRecord.id
                    moveRecord.position = index
                    moveRecord.uci = move.uci
                    moveRecord.moveTime = now
                    moveRecord
                }

            gameDao.insertReactive(gameRecord)
            moveDao.insertMultipleReactive(movesRecords)
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val attempts = 1_000_000
        val counter = AtomicInteger(0)

        runBlocking {
            (0 until attempts)
                .toList()
                .forEach { _ ->
                    persistGame(generateGame())
                    val count = counter.incrementAndGet()
                    if (count % 1_000 == 0) {
                        logger.info { "persisted $count games" }
                    }
                }
        }
    }

}
