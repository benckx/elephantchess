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
    fun summarizeMoveAnnotationsSkipsMismatchedDepthComparisonsAndCanFilterByActualMoveDepth() {
        val firstMove = "h2e2"
        val firstMoveBest = "c3c4"
        val secondMove = "b9c7"
        val secondMoveBest = "h9g7"

        val startBoard = Board(DEFAULT_START_FEN)
        val startFen = resetFullMoveCount(startBoard.outputFen())

        val firstMoveBestBoard = Board(DEFAULT_START_FEN)
        firstMoveBestBoard.registerMove(firstMoveBest)
        val firstMoveBestFen = resetFullMoveCount(firstMoveBestBoard.outputFen())

        val firstMoveBoard = Board(DEFAULT_START_FEN)
        firstMoveBoard.registerMove(firstMove)
        val firstMoveFen = resetFullMoveCount(firstMoveBoard.outputFen())

        val secondMoveBestBoard = Board(firstMoveBoard.outputFen())
        secondMoveBestBoard.registerMove(secondMoveBest)
        val secondMoveBestFen = resetFullMoveCount(secondMoveBestBoard.outputFen())

        val secondMoveBoard = Board(firstMoveBoard.outputFen())
        secondMoveBoard.registerMove(secondMove)
        val secondMoveFen = resetFullMoveCount(secondMoveBoard.outputFen())

        val analysisMap = mapOf(
            startFen to analysis(fen = startFen, bestMove = firstMoveBest, cp = 0, depth = 20),
            firstMoveBestFen to analysis(fen = firstMoveBestFen, cp = 180, depth = 20),
            firstMoveFen to analysis(fen = firstMoveFen, bestMove = secondMoveBest, cp = 0, depth = 20),
            secondMoveBestFen to analysis(fen = secondMoveBestFen, cp = 360, depth = 20),
            secondMoveFen to analysis(fen = secondMoveFen, cp = 0, depth = 18),
        )

        val allDepthsSummary = summarizeMoveAnnotations(
            moves = listOf(firstMove, secondMove),
            analysisMap = analysisMap,
        )
        val depth20Summary = summarizeMoveAnnotations(
            moves = listOf(firstMove, secondMove),
            analysisMap = analysisMap,
            actualMoveFilter = { it?.depth == 20 },
        )

        assertEquals(1, allDepthsSummary.annotatedMoves)
        assertEquals(0, allDepthsSummary.neutralMoves)
        assertEquals(1, allDepthsSummary.skippedMoves)
        assertEquals(2, allDepthsSummary.totalMoves)
        assertEquals(1, allDepthsSummary.categoryTotals.getValue(MoveAnnotationCategory.GOOD).count)
        assertEquals(0, allDepthsSummary.categoryTotals.getValue(MoveAnnotationCategory.BRILLIANT).count)

        assertEquals(1, depth20Summary.annotatedMoves)
        assertEquals(1, depth20Summary.totalMoves)
        assertEquals(1, depth20Summary.categoryTotals.getValue(MoveAnnotationCategory.GOOD).count)
        assertEquals(0, depth20Summary.categoryTotals.getValue(MoveAnnotationCategory.BRILLIANT).count)
    }

    @Test
    fun calculateCplRequiresMatchingDepthAtLeast18() {
        assertNull(
            calculateCpl(
                engineBest = analysis(fen = "engine", cp = 300, depth = 20),
                actualMove = analysis(fen = "actual", cp = 0, depth = 18),
            ),
        )
        assertNull(
            calculateCpl(
                engineBest = analysis(fen = "engine", cp = 300, depth = 17),
                actualMove = analysis(fen = "actual", cp = 0, depth = 17),
            ),
        )
    }

    private fun analysis(
        fen: String,
        cp: Int,
        depth: Int,
        bestMove: String? = null,
    ) = InfoLineResultDto(
        line = null,
        fen = fen,
        depth = depth,
        cp = cp,
        mate = null,
        pv = bestMove?.let(::listOf) ?: emptyList(),
        bestMove = bestMove,
        isCheckmate = false,
    )
}
