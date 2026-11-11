package io.elephantchess.scripts.janggi

import io.elephantchess.db.dao.codegen.tables.BotGame.BOT_GAME
import io.elephantchess.db.dao.codegen.tables.daos.BotGameMoveDao
import io.elephantchess.db.dao.codegen.tables.pojos.BotGameMove
import io.elephantchess.db.services.PlayerVsBotGameDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.insertReactive
import io.elephantchess.engines.EnginePool
import io.elephantchess.model.Engine.FAIRYSTOCKFISH
import io.elephantchess.model.OpeningMode.ENGINE_ONLY
import io.elephantchess.scripts.KoinScript
import io.elephantchess.servicelayer.dto.botgame.CreateBotGameRequest
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.PlayerVsBotGameService
import io.elephantchess.xiangqi.HalfMove
import io.elephantchess.xiangqi.JanggiBoard
import io.elephantchess.xiangqi.JanggiBoard.Companion.DEFAULT_JANGGI_START_FEN
import io.elephantchess.xiangqi.Variant.JANGGI
import io.elephantchess.xiangqi.testutils.JanggiGameMovesDto
import io.elephantchess.xiangqi.testutils.JanggiGameMovesDtoCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.koin.core.component.inject
import org.koin.core.context.stopKoin
import java.util.concurrent.Executors
import kotlin.system.exitProcess
import kotlin.time.Clock

/**
 * Run :scripts:createJanggiDrawGame --args='--username=benckx'.
 * Uses scripts.properties unless --profile and --config-location are supplied.
 */
object CreateJanggiDrawGame : KoinScript {

    private val users by inject<UserDaoService>()
    private val service by inject<PlayerVsBotGameService>()
    private val games by inject<PlayerVsBotGameDaoService>()
    private val dslContext by inject<DSLContext>()
    private val scope by inject<CoroutineScope>()

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            fun option(name: String): String? =
                args.firstOrNull { it.startsWith("--$name=") }?.substringAfter("=")

            val username = option("username") ?: "benckx"

            // The user plays the final (drawing) move from the browser, so it must land on their turn. We keep
            // the user's color equal to the side to move at startFen (even stalemate index). That also means
            // PlayerVsBotGameService.create does not play an opening bot move, so no engine is needed.
            fun firstStalemateIndex(game: JanggiGameMovesDto): Int {
                val board = JanggiBoard(game.startFen())
                return game.uciMoves.indexOfFirst { uci ->
                    board.registerMove(uci)
                    !board.isDraw() && !board.isInCheck(board.colorToPlay()) &&
                        board.listLegalMoves(board.colorToPlay()).none { it.from != it.to }
                }
            }

            val fixture = JanggiGameMovesDtoCache().randomGame { game ->
                firstStalemateIndex(game).let { index -> index >= 0 && index % 2 == 0 }
            }
            val startFen = fixture.startFen()
            val board = JanggiBoard(startFen)
            val finalIndex = firstStalemateIndex(fixture)
            check(finalIndex >= 0) { "Selected fixture has no stalemate" }
            val history = fixture.uciMoves.take(finalIndex)
            history.forEach { uci ->
                val move = HalfMove.parseMoveFromUci(uci)
                check(board.isLegalMove(move)) { "Illegal fixture move $uci" }
                board.registerMove(move)
            }
            val finalMove = HalfMove.parseMoveFromUci(fixture.uciMoves[finalIndex])
            check(board.isLegalMove(finalMove))
            val finalBoard = board.copy().apply { registerMove(finalMove) }
            check(!finalBoard.isInCheck(finalBoard.colorToPlay()))
            check(finalBoard.listLegalMoves(finalBoard.colorToPlay()).none { it.from != it.to })

            val engines = EnginePool(emptyMap(), Executors.newSingleThreadExecutor())
            initKoin(engines, option("profile"), option("config-location"))
            try {
                val user = users.findByUserName(username) ?: error("No local user '$username'")
                val response = service.create(
                    UserId(user.userType, user.id),
                    CreateBotGameRequest(
                        color = board.colorToPlay(),
                        depth = 1,
                        engine = FAIRYSTOCKFISH,
                        startFen = startFen,
                        openingMode = ENGINE_ONLY,
                        variant = JANGGI,
                    ),
                )
                // Backfill the fixture history directly instead of replaying it through the service, so no engine
                // is queried for the bot replies.
                dslContext.transactionCoroutine { cfg ->
                    val moveDao = BotGameMoveDao(cfg)
                    val now = Clock.System.now()
                    history.forEachIndexed { index, uci ->
                        val record = BotGameMove()
                        record.botGameId = response.gameId
                        record.position = index
                        record.uci = uci
                        record.eventTime = now
                        moveDao.insertReactive(record)
                    }
                    DSL.using(cfg)
                        .update(BOT_GAME)
                        .set(BOT_GAME.CURRENT_FEN, board.outputFen())
                        .set(BOT_GAME.CURRENT_HALF_MOVE_INDEX, history.size)
                        .set(BOT_GAME.LAST_UPDATED, now)
                        .where(BOT_GAME.ID.eq(response.gameId))
                        .awaitExecute()
                }
                check(games.listMoves(response.gameId) == history)
                val saved = games.fetchById(response.gameId)!!
                check((saved.startFen ?: DEFAULT_JANGGI_START_FEN) == startFen)
                check(saved.currentHalfMoveIndex == history.size)
                check(saved.currentFen == board.outputFen())
                println("Fixture: ${fixture.gameId}, before move ${finalIndex + 1}, with ${history.size} saved half-moves")
                println("Play ${finalMove.toUci()} (${finalMove.toAlgebraic()}) to draw. No engine is needed.")
                println("http://localhost:8080/playbot?id=${response.gameId}")
            } finally {
                scope.cancel()
                engines.close()
                stopKoin()
            }
        }

        exitProcess(0)
    }
}
