package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.WHITE
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.Position.Companion.parsePositionFromUci
import io.elephantchess.xiangqi.testutils.JanggiGameMovesDtoCache
import kotlin.test.*

class JanggiBoardTest {

    @Test
    fun `facing generals are legal but exposing check is not`() {
        val facing = board("K" to "e1", "k" to "e8", "P" to "e4")
        assertTrue(facing.isLegalMove(parseMoveFromUci("e4d4")))
        facing.registerMove("e4d4")
        assertTrue(facing.areGeneralsFacing())
        assertFalse(facing.isInCheck(WHITE))
        assertFalse(facing.isInCheck(BLACK))
        assertFalse(facing.isLegalMove(parseMoveFromUci("e8e7")))
        assertTrue(facing.isLegalMove(parseMoveFromUci("e8d8")))
        assertTrue(facing.isLegalMove(parseMoveFromUci("e8e8")))

        val pinned = board("K" to "e1", "k" to "d8", "P" to "e4", "r" to "e7")
        assertFalse(pinned.isLegalMove(parseMoveFromUci("e4d4")))
        assertTrue(pinned.isLegalMove(parseMoveFromUci("e4e5")))
    }

    @Test
    fun `passing cannot escape ordinary check and checkmate is recognized`() {
        val checked = JanggiBoard("9/4k4/9/4R4/9/9/9/9/3K5/9 b - - 0 1")
        assertTrue(checked.isInCheck(BLACK))
        assertFalse(checked.isLegalMove(parseMoveFromUci("e8e8")))
        assertFalse(checked.isCheckmated())
        val mate = JanggiBoard("9/4k4/3R1R3/4R4/9/9/9/9/3K5/9 b - - 0 1")
        assertTrue(mate.isCheckmated())
        assertFalse(mate.isStalemated())
        assertFalse(mate.isDraw())
    }

    @Test
    fun `a normal move breaks the sequence of passes`() {
        val board = JanggiBoard()
        board.registerMove("e1e1")
        board.registerMove("a6b6")
        board.registerMove("e1e1")
        assertFalse(board.isDraw())
        assertTrue(board.listLegalMoves(BLACK).isNotEmpty())
    }

    @Test
    fun `copies preserve Janggi state and loading FEN restores the starting position`() {
        val board = JanggiBoard()
        board.registerMove("a3b3")
        board.registerMove("a6b6")
        val copy = board.copy()
        assertEquals(board.outputFen(), copy.outputFen())
        assertEquals(board.listLegalMoves(WHITE), copy.listLegalMoves(WHITE))
        copy.registerMove("b3c3")
        assertFalse(copy.outputFen() == board.outputFen())
        copy.loadFen(JanggiBoard.DEFAULT_JANGGI_START_FEN)
        assertEquals(JanggiBoard.DEFAULT_JANGGI_START_FEN, copy.outputFen())
        assertEquals(32, copy.listAllPieces().size)
    }

    @Test
    fun `move history preserves piece identity and copies independently`() {
        val board = JanggiBoard(keepHistory = true)
        val piece = board.pieceAt(parsePositionFromUci("a3"))
        board.registerMove("a3b3")
        val history = assertNotNull(board.getHistory())
        assertEquals(piece, history.history.single().physicalPiece)
        assertEquals(1, board.copy(keepHistory = true).getHistory()!!.history.size)
        assertEquals(null, board.copy().getHistory())
        val copy = board.copy(keepHistory = true)
        copy.registerMove("a6b6")
        assertEquals(1, board.getHistory()!!.history.size)
        assertEquals(2, copy.getHistory()!!.history.size)
    }

    @Test
    fun `synthetic games replay with legal moves and expected outcomes`() {
        val games = JanggiGameMovesDtoCache().listAll()
        assertTrue(games.isNotEmpty())
        val checkmateGames = games.filter { it.outcome == "CHECKMATE" }
        assertTrue(checkmateGames.isNotEmpty())
        games.forEach { game ->
            val board = JanggiBoard(generateJanggiStartFen(JanggiSetup.valueOf(game.blueSetup), JanggiSetup.valueOf(game.redSetup)))
            game.uciMoves.forEachIndexed { index, uci ->
                val move = parseMoveFromUci(uci)
                assertTrue(
                    board.isLegalMove(move),
                    message = "Game ${game.gameId}, ply $index: $uci\n${board.print()}",
                )
                board.registerMove(move)
                if (move.from != move.to) {
                    assertFalse(
                        board.isInCheck(board.colorToPlay().reverse()),
                        message = "Game ${game.gameId}, ply $index: $uci",
                    )
                }
            }
            val actualOutcome = when {
                board.isCheckmated() -> "CHECKMATE"
                board.isDraw() -> "DRAW"
                board.isStalemated() -> "STALEMATE"
                else -> "ONGOING"
            }
            assertEquals(game.outcome, actualOutcome, "Game ${game.gameId}")
        }
    }

    @Test
    fun `passing accepts bikjang even when in check`() {
        val game = JanggiGameMovesDtoCache().findByGameId("cb4951a3")
        val board = JanggiBoard(generateJanggiStartFen(JanggiSetup.valueOf(game.blueSetup), JanggiSetup.valueOf(game.redSetup)))
        game.uciMoves.dropLast(1).forEach { uci ->
            assertTrue(board.isLegalMove(parseMoveFromUci(uci)))
            board.registerMove(uci)
        }
        assertEquals("e1e1", game.uciMoves.last())
        assertTrue(board.isInCheck(board.colorToPlay()))
        assertTrue(board.areGeneralsFacing())
        assertTrue(board.isLegalMove(parseMoveFromUci(game.uciMoves.last())))
        board.registerMove(game.uciMoves.last())
        assertTrue(board.isDraw())
        assertFalse(board.isCheckmated())
        assertFalse(board.isStalemated())
    }

    @Test
    fun `passing preserves pieces and history and two consecutive passes draw`() {
        val board = JanggiBoard(keepHistory = true)
        val placement = board.outputFen(abridged = true)
        val piece = board.pieceAt(parsePositionFromUci("e1"))
        assertTrue(board.isLegalMove(parseMoveFromUci("e1e1")))
        assertEquals(null, board.registerMove("e1e1"))
        assertEquals(BLACK, board.colorToPlay())
        assertEquals(placement, board.outputFen(abridged = true))
        assertEquals(piece, board.pieceAt(parsePositionFromUci("e1")))
        assertEquals(null, board.getHistory()!!.history.single().capture)
        assertTrue(board.isLegalMove(parseMoveFromUci("e8e8")))
        board.registerMove("e8e8")
        assertEquals(WHITE, board.colorToPlay())
        assertEquals(2, board.getHistory()!!.history.size)
        assertTrue(board.outputFen().endsWith(" 2"))
        assertTrue(board.isDraw())
        assertTrue(board.listLegalMoves(WHITE).isEmpty())
        assertFalse(board.isStalemated())
        assertTrue(board.copy().isDraw())
        board.loadFen(JanggiBoard.DEFAULT_JANGGI_START_FEN)
        assertFalse(board.isDraw())
        board.registerMove("e1e1")
        val copy = board.copy()
        copy.registerMove("e8e8")
        assertTrue(copy.isDraw())
        assertFalse(board.isDraw())
        assertFalse(XiangqiBoard().isLegalMove(parseMoveFromUci("e0e0")))
    }

    @Test
    fun `board creation respects the variant and rejects Janggi in XiangqiBoard`() {
        assertIs<JanggiBoard>(AbstractXiangqiBoard.create(Variant.JANGGI))
        assertEquals(Variant.JANGGI, JanggiBoard().copy().variant)
        assertEquals(Variant.MANCHU, AbstractXiangqiBoard.create(Variant.MANCHU).variant)
        assertEquals(XiangqiBoard.MANCHU_START_FEN, AbstractXiangqiBoard.create(Variant.MANCHU).outputFen())
        assertFailsWith<IllegalArgumentException> { XiangqiBoard(variant = Variant.JANGGI) }
        assertEquals("janggitraditional", Variant.JANGGI.fairyStockfishVariantName())
    }

    private fun board(vararg pieces: Pair<String, String>): JanggiBoard {
        val byPosition = pieces.associate { (piece, uci) -> parsePositionFromUci(uci) to piece }
        val placement = (9 downTo 0).joinToString("/") { rank ->
            (0..8).joinToString("") { file -> byPosition[Position(file, rank)] ?: "1" }
        }
        return JanggiBoard("$placement w - - 0 1")
    }
}
