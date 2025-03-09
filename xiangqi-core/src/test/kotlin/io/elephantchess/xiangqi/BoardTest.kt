package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.testutils.GameMovesDtoCache
import io.elephantchess.xiangqi.testutils.Ops.moves
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.Test

class BoardTest {

    private val logger = KotlinLogging.logger {}
    private val cache = GameMovesDtoCache()

    @Test
    fun parseUciMovesTest01() {
        cache.listAll().forEach { entry ->
            val board = Board()
            board.registerMoves(entry.moves())
            logger.trace { "gameId: ${entry.gameId}, final fen ${board.outputFen()}" }
            assert(board.getHistory() == null)
            if (board.isCheckmated()) {
                logger.info { "game ${entry.gameId} ends in checkmate" }
            } else if (board.isStalemated()) {
                logger.info { "game ${entry.gameId} ends in stalemate" }
            }
        }
    }

}
