package io.elephantchess.scripts.janggi

import io.elephantchess.db.services.UserDaoService
import io.elephantchess.engines.EnginePool
import io.elephantchess.model.Engine
import io.elephantchess.model.OpeningMode
import io.elephantchess.scripts.KoinScript
import io.elephantchess.servicelayer.dto.botgame.CreateBotGameRequest
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.PlayerVsBotGameService
import io.elephantchess.xiangqi.HalfMove
import io.elephantchess.xiangqi.JanggiBoard
import io.elephantchess.xiangqi.Variant
import io.elephantchess.xiangqi.testutils.JanggiGameMovesDtoCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import org.koin.core.context.stopKoin
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Run :scripts:createJanggiDrawGame --args='--username=benckx'.
 * Uses scripts.properties unless --profile and --config-location are supplied.
 */
object CreateJanggiDrawGame : KoinScript {

    private val users by inject<UserDaoService>()
    private val service by inject<PlayerVsBotGameService>()

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            fun option(name: String): String? =
                args.firstOrNull { it.startsWith("--$name=") }?.substringAfter("=")

            val username = option("username") ?: error("Supply --username=<existing local username>")
            // This fixture continues with a pass after move 155. PvB stops at that first no-pass stalemate.
            val fixture = JanggiGameMovesDtoCache().findByGameId("7f0d05cd")
            val board = JanggiBoard()
            fixture.uciMoves.take(154).forEach { board.registerMove(it) }
            val finalMove = HalfMove.parseMoveFromUci(fixture.uciMoves[154])
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
                        engine = Engine.FAIRYSTOCKFISH,
                        startFen = board.outputFen(),
                        openingMode = OpeningMode.ENGINE_ONLY,
                        variant = Variant.JANGGI,
                    ),
                )
                println("Fixture: ${fixture.gameId}, before move 155")
                println("Play ${finalMove.toUci()} (${finalMove.toAlgebraic()}) to draw. No engine is needed.")
                println("http://localhost:8080/playbot?id=${response.gameId}")
            } finally {
                getKoin().get<CoroutineScope>().cancel()
                engines.close()
                stopKoin()
            }
        }
        exitProcess(0)
    }
}
