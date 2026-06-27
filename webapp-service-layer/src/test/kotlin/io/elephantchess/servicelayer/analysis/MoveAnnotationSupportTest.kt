package io.elephantchess.servicelayer.analysis

import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoveAnnotationSupportTest {
    private companion object {
        const val MATE_NEGATIVE_TWELVE_HEURISTIC_CP = -7_930
    }

    @Test
    fun `summarizeMoveAnnotations skips mismatched-depth comparisons and can filter by actual-move depth`() {
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
    fun `calculateCpl requires matching depth of at least 18`() {
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

    @Test
    fun `collectMoveAnnotations returns tooltip calculation data`() {
        val move = "h2e2"
        val bestMove = "c3c4"

        val startBoard = Board(DEFAULT_START_FEN)
        val startFen = resetFullMoveCount(startBoard.outputFen())

        val bestMoveBoard = Board(DEFAULT_START_FEN)
        bestMoveBoard.registerMove(bestMove)
        val bestMoveFen = resetFullMoveCount(bestMoveBoard.outputFen())

        val actualMoveBoard = Board(DEFAULT_START_FEN)
        actualMoveBoard.registerMove(move)
        val actualMoveFen = resetFullMoveCount(actualMoveBoard.outputFen())

        val annotations = collectMoveAnnotations(
            moves = listOf(move),
            analysisMap = mapOf(
                startFen to analysis(fen = startFen, bestMove = bestMove, cp = 0, depth = 20),
                bestMoveFen to analysis(fen = bestMoveFen, cp = 360, depth = 20),
                actualMoveFen to analysis(fen = actualMoveFen, cp = 0, depth = 20),
            ),
        )

        assertEquals(
            listOf(
                MoveAnnotationDetails(
                    moveIndex = 0,
                    category = MoveAnnotationCategory.BRILLIANT,
                    cpl = 360,
                    engineCp = 360,
                    actualMoveCp = 0,
                ),
            ),
            annotations,
        )
    }

    @Test
    fun `sample BLUNDER cases match sample data`() {
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.BLUNDER,
                expectedCpl = -2577,
                actualCp = 885,
                expectedActualCp = 885,
                engineCp = -1692,
                expectedEngineCp = -1692,
            ),
        )
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.BLUNDER,
                expectedCpl = -328,
                actualCp = 320,
                expectedActualCp = 320,
                engineCp = -8,
                expectedEngineCp = -8,
            ),
        )
    }

    @Test
    fun `sample MISTAKE cases match sample data`() {
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.MISTAKE,
                expectedCpl = -122,
                actualCp = 190,
                expectedActualCp = 190,
                engineCp = 68,
                expectedEngineCp = 68,
            ),
        )
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.MISTAKE,
                expectedCpl = -141,
                actualCp = 206,
                expectedActualCp = 206,
                engineCp = 65,
                expectedEngineCp = 65,
            ),
        )
    }

    @Test
    fun `sample INACCURACY cases match sample data`() {
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.INACCURACY,
                expectedCpl = -66,
                actualCp = -1,
                expectedActualCp = -1,
                engineCp = -67,
                expectedEngineCp = -67,
            ),
        )
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.INACCURACY,
                expectedCpl = -68,
                actualCp = 58,
                expectedActualCp = 58,
                engineCp = -10,
                expectedEngineCp = -10,
            ),
        )
    }

    @Test
    fun `sample INTERESTING cases match sample data`() {
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.INTERESTING,
                expectedCpl = 65,
                actualCp = -2232,
                expectedActualCp = -2232,
                engineCp = -2167,
                expectedEngineCp = -2167,
            ),
        )
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.INTERESTING,
                expectedCpl = 94,
                actualCp = -3901,
                expectedActualCp = -3901,
                engineCp = -3807,
                expectedEngineCp = -3807,
            ),
        )
    }

    @Test
    fun `sample GOOD cases match sample data`() {
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.GOOD,
                expectedCpl = 180,
                actualCp = 606,
                expectedActualCp = 606,
                engineCp = 786,
                expectedEngineCp = 786,
            ),
        )
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.GOOD,
                expectedCpl = 107,
                actualCp = -1013,
                expectedActualCp = -1013,
                engineCp = -906,
                expectedEngineCp = -906,
            ),
        )
    }

    @Test
    fun `sample BRILLIANT cases match sample data`() {
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.BRILLIANT,
                expectedCpl = 890,
                actualMate = -12,
                expectedActualCp = MATE_NEGATIVE_TWELVE_HEURISTIC_CP,
                engineCp = -7040,
                expectedEngineCp = -7040,
            ),
        )
        assertMatchesSample(
            AnnocationTestCase(
                expectedCategory = MoveAnnotationCategory.BRILLIANT,
                expectedCpl = 312,
                actualCp = -2917,
                expectedActualCp = -2917,
                engineCp = -2605,
                expectedEngineCp = -2605,
            ),
        )
    }

    private fun assertMatchesSample(case: AnnocationTestCase) {
        val engineBest = analysis(
            fen = "engine",
            cp = case.engineCp,
            depth = 20,
        )
        val actualMove = analysis(
            fen = "actual",
            cp = case.actualCp,
            mate = case.actualMate,
            depth = 20,
        )

        assertEquals(case.expectedCpl, calculateCpl(engineBest, actualMove))
        assertEquals(
            MoveAnnotationResult(
                category = case.expectedCategory,
                cpl = case.expectedCpl,
                engineCp = case.expectedEngineCp,
                actualMoveCp = case.expectedActualCp,
            ),
            calculateMoveAnnotation(engineBest, actualMove),
        )
    }

    /**
     * Creates a minimal test info line.
     *
     * @param cp centipawn score, when present
     * @param mate mate score, when present
     */
    private fun analysis(
        fen: String,
        cp: Int?,
        depth: Int,
        mate: Int? = null,
        bestMove: String? = null,
    ): InfoLineResultDto {
        require((cp != null).xor(mate != null)) { "Exactly one of cp or mate must be provided" }
        return InfoLineResultDto(
            line = null,
            fen = fen,
            depth = depth,
            cp = cp,
            mate = mate,
            pv = bestMove?.let(::listOf) ?: emptyList(),
            bestMove = bestMove,
            isCheckmate = false,
        )
    }

    private data class AnnocationTestCase(
        val expectedCategory: MoveAnnotationCategory,
        val expectedCpl: Int,
        val actualCp: Int? = null,
        val actualMate: Int? = null,
        val expectedActualCp: Int,
        val engineCp: Int,
        val expectedEngineCp: Int,
    ) {
        init {
            require((actualCp != null).xor(actualMate != null)) { "Exactly one of actualCp or actualMate must be provided" }
        }
    }
}
