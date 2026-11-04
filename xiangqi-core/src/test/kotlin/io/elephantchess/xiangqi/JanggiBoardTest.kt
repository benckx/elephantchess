package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.Color.BLACK
import io.elephantchess.xiangqi.Color.WHITE
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.Position.Companion.parsePositionFromUci
import io.elephantchess.xiangqi.testutils.JanggiGameMovesDtoCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JanggiBoardTest {

    @Test
    fun generalsAndGuardsFollowPalaceLines() {
        assertEquals(
            setOf("d0", "e0", "f0", "d1", "f1", "d2", "e2", "f2"),
            targets(board("K" to "e1"), "e1"),
        )
        assertEquals(setOf("d1", "e0", "e1"), targets(board("A" to "d0"), "d0"))
        assertEquals(setOf("d0", "d2", "e1"), targets(board("A" to "d1"), "d1"))
        assertEquals(setOf("e8", "e9", "f8"), targets(board("a" to "f9"), "f9"))
    }

    @Test
    fun palaceMovesCannotLeavePalaceOrCaptureFriendlyPieces() {
        assertEquals(setOf("d1", "e0"), targets(board("A" to "d0", "K" to "e1"), "d0"))
        assertEquals(
            setOf("e0", "e2", "f1"),
            targets(board("A" to "e1", "P" to "d1", "P" to "d0", "P" to "f0", "P" to "d2", "P" to "f2"), "e1"),
        )
    }

    @Test
    fun horseUsesTheSameBlockedLegRuleAsXiangqi() {
        val board = board("N" to "e4", "p" to "e5")
        assertEquals(setOf("c3", "c5", "d2", "f2", "g3", "g5"), targets(board, "e4"))
    }

    @Test
    fun elephantsHaveEightLongMovesWithoutARiverRestriction() {
        assertEquals(
            setOf("b2", "b6", "c1", "c7", "g1", "g7", "h2", "h6"),
            targets(board("B" to "e4"), "e4"),
        )
    }

    @Test
    fun elephantsAreBlockedAtEitherIntermediatePoint() {
        val firstBlocked = targets(board("B" to "e4", "p" to "e5"), "e4")
        assertFalse("c7" in firstBlocked)
        assertFalse("g7" in firstBlocked)
        val secondBlocked = targets(board("B" to "e4", "p" to "f6"), "e4")
        assertFalse("g7" in secondBlocked)
        assertTrue("c7" in secondBlocked)
        assertFalse("g7" in targets(board("B" to "e4", "P" to "g7"), "e4"))
        assertTrue("g7" in targets(board("B" to "e4", "p" to "g7"), "e4"))
    }

    @Test
    fun chariotsFollowOnlyMarkedPalaceDiagonals() {
        val corners = targets(board("R" to "d0"), "d0")
        assertTrue("e1" in corners)
        assertTrue("f2" in corners)
        assertFalse("g3" in corners)
        assertFalse("e1" in targets(board("R" to "d0", "P" to "e1"), "d0"))
        assertFalse("f2" in targets(board("R" to "d0", "p" to "e1"), "d0"))
        assertTrue("e1" in targets(board("R" to "d0", "p" to "e1"), "d0"))
        assertFalse("e2" in targets(board("R" to "d1"), "d1"))
        assertTrue("f9" in targets(board("R" to "d7"), "d7"))
    }

    @Test
    fun cannonsRequireExactlyOneNonCannonPivotEvenForQuietMoves() {
        assertTrue(targets(board("C" to "e4"), "e4").isEmpty())
        assertEquals(
            setOf("e6", "e7"),
            targets(board("C" to "e4", "P" to "e5", "p" to "e7", "p" to "e8"), "e4"),
        )
        assertEquals(setOf("e6"), targets(board("C" to "e4", "p" to "e5", "P" to "e7"), "e4"))
        assertTrue(targets(board("C" to "e4", "c" to "e5"), "e4").isEmpty())
        assertTrue(targets(board("C" to "e4", "C" to "e5"), "e4").isEmpty())
        assertEquals(setOf("e6"), targets(board("C" to "e4", "P" to "e5", "c" to "e7"), "e4"))
        assertEquals(setOf("e6"), targets(board("C" to "e4", "P" to "e5", "C" to "e7"), "e4"))
    }

    @Test
    fun cannonsJumpPalaceCentersButNeverTurnAtTheCenter() {
        assertEquals(setOf("f2"), targets(board("C" to "d0", "A" to "e1"), "d0"))
        assertTrue(targets(board("C" to "d0", "c" to "e1"), "d0").isEmpty())
        assertTrue(targets(board("C" to "d0", "A" to "e1", "c" to "f2"), "d0").isEmpty())
        assertEquals(setOf("f2"), targets(board("C" to "d0", "A" to "e1", "p" to "f2"), "d0"))
        assertEquals(setOf("f9"), targets(board("C" to "d7", "a" to "e8"), "d7"))
    }

    @Test
    fun soldiersMoveSidewaysImmediatelyAndForwardOnEnemyPalaceDiagonals() {
        assertEquals(setOf("d3", "f3", "e4"), targets(board("P" to "e3"), "e3"))
        assertEquals(setOf("d6", "f6", "e5"), targets(board("p" to "e6"), "e6"))
        assertEquals(setOf("c7", "e7", "d8", "e8"), targets(board("P" to "d7"), "d7"))
        assertEquals(setOf("d8", "f8", "e9", "d9", "f9"), targets(board("P" to "e8"), "e8"))
        assertEquals(setOf("d1", "f1", "e2"), targets(board("P" to "e1"), "e1"))
        assertEquals(setOf("d1", "f1", "e0", "d0", "f0"), targets(board("p" to "e1"), "e1"))
    }

    @Test
    fun facingGeneralsAreLegalButExposingCheckIsNot() {
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
    fun checksIncludePalaceDiagonals() {
        assertTrue(board("K" to "d0", "k" to "d8", "r" to "f2").isInCheck(WHITE))
        assertTrue(board("K" to "d0", "k" to "d8", "c" to "f2", "P" to "e1").isInCheck(WHITE))
        assertFalse(board("K" to "d0", "k" to "d8", "c" to "f2", "C" to "e1").isInCheck(WHITE))
    }

    @Test
    fun cannotPassOutOfOrdinaryCheckAndRecognizesCheckmate() {
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
    fun aNormalMoveBreaksTheSequenceOfPasses() {
        val board = JanggiBoard()
        board.registerMove("e1e1")
        board.registerMove("a6b6")
        board.registerMove("e1e1")
        assertFalse(board.isDraw())
        assertTrue(board.listLegalMoves(BLACK).isNotEmpty())
    }

    @Test
    fun copiesAndFenReloadsPreserveJanggiRulesAndState() {
        val board = JanggiBoard()
        board.registerMove("a3b3")
        board.registerMove("a6b6")
        val copy = board.copy()
        assertEquals(board.outputFen(), copy.outputFen())
        assertEquals(board.listLegalMoves(WHITE), copy.listLegalMoves(WHITE))
        copy.registerMove("b3c3")
        assertFalse(copy.outputFen() == board.outputFen())
        copy.loadFen(JanggiBoard.DEFAULT_START_FEN)
        assertEquals(JanggiBoard.DEFAULT_START_FEN, copy.outputFen())
        assertEquals(32, copy.listAllPieces().size)
    }

    @Test
    fun moveHistoryPreservesPieceIdentityAndCopiesIndependently() {
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
    fun replaySyntheticGamesCheckingEveryMove() {
        val games = JanggiGameMovesDtoCache().listAll()
        assertEquals(100, games.size)
        games.forEach { game ->
            val board = JanggiBoard()
            game.uciMoves.forEachIndexed { index, uci ->
                val move = parseMoveFromUci(uci)
                assertTrue(board.isLegalMove(move), "Game ${game.gameId}, ply $index: $uci\n${board.print()}")
                board.registerMove(move)
                if (move.from != move.to) {
                    assertFalse(board.isInCheck(board.colorToPlay().reverse()), "Game ${game.gameId}, ply $index: $uci")
                }
            }
        }
    }

    @Test
    fun acceptsBikjangByPassingEvenWhenInCheck() {
        val game = JanggiGameMovesDtoCache().findByGameId("233d96b7")
        val board = JanggiBoard()
        game.uciMoves.dropLast(1).forEach { uci ->
            assertTrue(board.isLegalMove(parseMoveFromUci(uci)))
            board.registerMove(uci)
        }
        assertEquals("e8e8", game.uciMoves.last())
        assertTrue(board.isInCheck(BLACK))
        assertTrue(board.areGeneralsFacing())
        assertTrue(board.isLegalMove(parseMoveFromUci(game.uciMoves.last())))
        board.registerMove(game.uciMoves.last())
        assertTrue(board.isDraw())
        assertFalse(board.isCheckmated())
        assertFalse(board.isStalemated())
    }

    @Test
    fun passingPreservesPiecesAndHistoryAndAdvancesTheTurn() {
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
        board.loadFen(JanggiBoard.DEFAULT_START_FEN)
        assertFalse(board.isDraw())
        board.registerMove("e1e1")
        val copy = board.copy()
        copy.registerMove("e8e8")
        assertTrue(copy.isDraw())
        assertFalse(board.isDraw())
        assertFalse(XiangqiBoard().isLegalMove(parseMoveFromUci("e0e0")))
    }

    @Test
    fun createsVariantSpecificBoardsWithoutInferringJanggiFromFen() {
        assertIs<JanggiBoard>(AbstractXiangqiBoard.create(Variant.JANGGI))
        assertEquals(Variant.JANGGI, JanggiBoard().copy().variant)
        assertEquals(Variant.MANCHU, AbstractXiangqiBoard.create(Variant.MANCHU).variant)
        assertEquals(XiangqiBoard.MANCHU_START_FEN, AbstractXiangqiBoard.create(Variant.MANCHU).outputFen())
        assertFailsWith<IllegalArgumentException> { XiangqiBoard(variant = Variant.JANGGI) }
        assertEquals("janggitraditional", Variant.JANGGI.fairyStockfishVariantName())
    }

    @Test
    fun perftMatchesFairyStockfish14TraditionalJanggi() {
        assertEquals(32, perft(JanggiBoard(), 1))
        assertEquals(1024, perft(JanggiBoard(), 2))
        assertEquals(33000, perft(JanggiBoard(), 3))
        val palacePosition = JanggiBoard("3r5/4k4/9/3P5/9/4P4/9/9/4K4/3R5 w - - 0 1")
        assertEquals(453, perft(palacePosition, 2))
    }

    private fun perft(board: JanggiBoard, depth: Int): Int =
        if (depth == 0) {
            1
        } else {
            board.listLegalMoves(board.colorToPlay()).sumOf { move ->
                val copy = board.copy()
                copy.registerMove(move)
                perft(copy, depth - 1)
            }
        }

    private fun targets(board: JanggiBoard, from: String): Set<String> {
        val position = parsePositionFromUci(from)
        return board.listAllMoves(PieceAtPosition(board.pieceAt(position), position)).map { it.to.toUci() }.toSet()
    }

    private fun board(vararg pieces: Pair<String, String>): JanggiBoard {
        val byPosition = pieces.associate { (piece, uci) -> parsePositionFromUci(uci) to piece }
        val placement = (9 downTo 0).joinToString("/") { rank ->
            (0..8).joinToString("") { file -> byPosition[Position(file, rank)] ?: "1" }
        }
        return JanggiBoard("$placement w - - 0 1")
    }
}
