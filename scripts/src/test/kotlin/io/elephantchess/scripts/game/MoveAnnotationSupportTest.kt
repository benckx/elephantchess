package io.elephantchess.scripts.game

import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoveAnnotationSupportTest {

    @Test
    fun `calculateCpl mirrors centipawn delta`() {
        assertEquals(-320, calculateCpl(infoLine(cp = 120), infoLine(cp = 440)))
        assertEquals(210, calculateCpl(infoLine(cp = 50), infoLine(cp = -160)))
    }

    @Test
    fun `calculateMoveAnnotation maps negative cpl to player mistakes`() {
        assertEquals(
            MoveAnnotationResult(MoveAnnotationCategory.BLUNDER, -300),
            calculateMoveAnnotation(infoLine(cp = 100), infoLine(cp = 400)),
        )
        assertEquals(
            MoveAnnotationResult(MoveAnnotationCategory.MISTAKE, -150),
            calculateMoveAnnotation(infoLine(cp = 120), infoLine(cp = 270)),
        )
        assertEquals(
            MoveAnnotationResult(MoveAnnotationCategory.INACCURACY, -60),
            calculateMoveAnnotation(infoLine(cp = 80), infoLine(cp = 140)),
        )
    }

    @Test
    fun `calculateMoveAnnotation maps positive cpl to engine-favored annotations`() {
        assertEquals(
            MoveAnnotationResult(MoveAnnotationCategory.BRILLIANT, 320),
            calculateMoveAnnotation(infoLine(cp = 400), infoLine(cp = 80)),
        )
        assertEquals(
            MoveAnnotationResult(MoveAnnotationCategory.GOOD, 140),
            calculateMoveAnnotation(infoLine(cp = 200), infoLine(cp = 60)),
        )
        assertEquals(
            MoveAnnotationResult(MoveAnnotationCategory.INTERESTING, 70),
            calculateMoveAnnotation(infoLine(cp = 110), infoLine(cp = 40)),
        )
    }

    @Test
    fun `calculateMoveAnnotation ignores small deltas and checkmates`() {
        assertNull(calculateMoveAnnotation(infoLine(cp = 100), infoLine(cp = 70)))
        assertNull(calculateMoveAnnotation(infoLine(cp = 100), infoLine(cp = 400, isCheckmate = true)))
    }

    @Test
    fun `calculateCpl maps mate scores the same way as javascript heuristics`() {
        assertEquals(240, calculateCpl(infoLine(mate = 10), infoLine(cp = 7954)))
        assertEquals(240, calculateCpl(infoLine(cp = -7954), infoLine(mate = -10)))
    }

    @Test
    fun `summarizeMoveAnnotations counts annotated neutral and skipped moves`() {
        val moves = listOf("c3c4", "b9c7", "g3g4")
        val board = Board(DEFAULT_START_FEN)

        val startFen = resetFullMoveCount(board.outputFen())
        val bestFen1 = board.afterMoveFen("h0g2")
        board.registerMove(moves[0])
        val actualFen1 = resetFullMoveCount(board.outputFen())

        val actualFen2 = board.afterMoveFen(moves[1])
        board.registerMove(moves[1])
        val fenBeforeMove3 = resetFullMoveCount(board.outputFen())

        val analysisMap = mapOf(
            startFen to infoLine(fen = startFen, bestMove = "h0g2"),
            bestFen1 to infoLine(fen = bestFen1, cp = 100),
            actualFen1 to infoLine(fen = actualFen1, cp = 400, bestMove = moves[1]),
            actualFen2 to infoLine(fen = actualFen2, cp = 80),
        )

        val summary = summarizeMoveAnnotations(moves, analysisMap)

        assertEquals(3, summary.totalMoves)
        assertEquals(1, summary.annotatedMoves)
        assertEquals(1, summary.neutralMoves)
        assertEquals(1, summary.skippedMoves)
        assertEquals(1, summary.categoryTotals.getValue(MoveAnnotationCategory.BLUNDER).count)
        assertEquals(-300L, summary.categoryTotals.getValue(MoveAnnotationCategory.BLUNDER).totalCpl)
        assertEquals(0, summary.categoryTotals.getValue(MoveAnnotationCategory.MISTAKE).count)
        assertNull(summary.categoryTotals.getValue(MoveAnnotationCategory.MISTAKE).averageCpl())
        assertEquals(fenBeforeMove3, resetFullMoveCount(board.outputFen()))
    }

    private fun infoLine(
        fen: String = "fen",
        cp: Int? = null,
        mate: Int? = null,
        bestMove: String? = null,
        isCheckmate: Boolean = false,
    ) = InfoLineResultDto(
        line = null,
        fen = fen,
        depth = 20,
        cp = cp,
        mate = mate,
        pv = listOfNotNull(bestMove),
        bestMove = bestMove,
        isCheckmate = isCheckmate,
    )

    private fun Board.afterMoveFen(move: String): String {
        val copy = copy()
        copy.registerMove(move)
        return resetFullMoveCount(copy.outputFen())
    }
}
