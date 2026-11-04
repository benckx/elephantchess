package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.testutils.GameMovesDtoCache
import io.elephantchess.xiangqi.testutils.ManchuGameMovesDtoCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XiangqiBoardTest {

    @Test
    fun replayXiangqiAndManchuFixturesPreservingBoardState() {
        val games = GameMovesDtoCache().listAll().map { it to Board.DEFAULT_START_FEN } +
            ManchuGameMovesDtoCache().listAll().map { it to Board.MANCHU_START_FEN }
        games.forEach { (game, fen) ->
            val board = XiangqiBoard(fen)
            game.uciMoves.forEach { uci ->
                board.registerMove(uci)
            }
            assertEquals(board.variant, board.copy().variant)
            assertEquals(board.outputFen(), board.copy().outputFen())
        }
    }

    @Test
    fun xiangqiStillForbidsFacingGenerals() {
        val board = XiangqiBoard("9/4k4/9/9/9/4P4/9/9/4K4/9 w - - 0 0")
        assertFalse(board.isLegalMove(parseMoveFromUci("e4d4")))
        assertTrue(board.isLegalMove(parseMoveFromUci("e4e5")))
    }

    @Test
    fun legacyBoardKeepsItsConcreteCopyType() {
        val board = Board(Board.MANCHU_START_FEN)
        val copy: Board = board.copy()
        assertEquals(Variant.MANCHU, copy.variant)
        assertEquals(board.outputFen(), copy.outputFen())
    }

    @Test
    fun moveCountsMatchFairyStockfish14BeforeAndAfterExtraction() {
        assertEquals(44, XiangqiBoard().listLegalMoves(Color.WHITE).size)
        assertEquals(79666, perft(XiangqiBoard(), 3))
        assertEquals(860, perft(XiangqiBoard(Board.MANCHU_START_FEN), 2))
        assertEquals(860, perft(Board(Board.MANCHU_START_FEN), 2))
    }

    private fun perft(board: AbstractXiangqiBoard, depth: Int): Int =
        if (depth == 0) {
            1
        } else {
            board.listLegalMoves(board.colorToPlay()).sumOf { move ->
                val copy = board.copy()
                copy.registerMove(move)
                perft(copy, depth - 1)
            }
        }
}
