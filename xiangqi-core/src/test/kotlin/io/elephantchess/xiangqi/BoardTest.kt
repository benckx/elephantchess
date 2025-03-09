package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.testutils.GameMovesDtoCache
import io.elephantchess.xiangqi.testutils.Ops.moves
import kotlin.test.Test

class BoardTest {

    private val cache = GameMovesDtoCache()

    @Test
    fun parseUciMovesTest01() {
        cache.listAll().forEach { entry ->
            val board = Board()
            board.registerMoves(entry.moves())
            assert(board.getHistory() == null)
        }
    }

}
