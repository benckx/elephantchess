package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.testutils.GameMovesDtoCache
import io.elephantchess.xiangqi.testutils.Ops.moves
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun usesWhiteBlackCoreColorSemantics() {
        val board = Board()

        assertEquals(Color.WHITE, board.colorToPlay())
        assertEquals(Color.BLACK, Color.WHITE.reverse())
        assertEquals(Color.WHITE, Color.BLACK.reverse())
        assertEquals(Color.WHITE, Color.fromChar('R'))
        assertEquals(Color.BLACK, Color.fromChar('b'))
        assertEquals(Color.BLACK, Color.RED.reverse())
    }
}
