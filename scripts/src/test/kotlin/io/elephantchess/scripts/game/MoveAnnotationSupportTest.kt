package io.elephantchess.scripts.game

import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoveAnnotationSupportTest {
    private companion object {
        const val MAX_ABS_CP = 7_706
        const val MAX_MATE = 40
        const val MATE_BONUS_PER_MOVE = 8
    }

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

    @Test
    fun collectMoveAnnotationsReturnsTooltipCalculationData() {
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
    fun sampleBlunderCasesMatchSampleData() {
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.BLUNDER,
                expectedCpl = -2577,
                actualCp = 885,
                engineCp = -1692,
            ),
        )
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.BLUNDER,
                expectedCpl = -328,
                actualCp = 320,
                engineCp = -8,
            ),
        )
    }

    @Test
    fun sampleMistakeCasesMatchSampleData() {
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.MISTAKE,
                expectedCpl = -122,
                actualCp = 190,
                engineCp = 68,
            ),
        )
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.MISTAKE,
                expectedCpl = -141,
                actualCp = 206,
                engineCp = 65,
            ),
        )
    }

    @Test
    fun sampleInaccuracyCasesMatchSampleData() {
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.INACCURACY,
                expectedCpl = -66,
                actualCp = -1,
                engineCp = -67,
            ),
        )
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.INACCURACY,
                expectedCpl = -68,
                actualCp = 58,
                engineCp = -10,
            ),
        )
    }

    @Test
    fun sampleInterestingCasesMatchSampleData() {
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.INTERESTING,
                expectedCpl = 65,
                actualCp = -2232,
                engineCp = -2167,
            ),
        )
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.INTERESTING,
                expectedCpl = 94,
                actualCp = -3901,
                engineCp = -3807,
            ),
        )
    }

    @Test
    fun sampleGoodCasesMatchSampleData() {
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.GOOD,
                expectedCpl = 180,
                actualCp = 606,
                engineCp = 786,
            ),
        )
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.GOOD,
                expectedCpl = 107,
                actualCp = -1013,
                engineCp = -906,
            ),
        )
    }

    @Test
    fun sampleBrilliantCasesMatchSampleData() {
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.BRILLIANT,
                expectedCpl = 890,
                actualMate = -12,
                engineCp = -7040,
            ),
        )
        assertMatchesSample(
            AnnotationCase(
                expectedCategory = MoveAnnotationCategory.BRILLIANT,
                expectedCpl = 978,
                actualCp = -31752,
                engineCp = -6728,
            ),
        )
    }

    private fun assertMatchesSample(case: AnnotationCase) {
        val engineBest = analysis(
            fen = "engine",
            cp = case.engineCp,
            mate = case.engineMate,
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

    private fun analysis(
        fen: String,
        cp: Int?,
        depth: Int,
        mate: Int? = null,
        bestMove: String? = null,
    ) = InfoLineResultDto(
        line = null,
        fen = fen,
        depth = depth,
        cp = cp,
        mate = mate,
        pv = bestMove?.let(::listOf) ?: emptyList(),
        bestMove = bestMove,
        isCheckmate = false,
    )

    private data class AnnotationCase(
        val expectedCategory: MoveAnnotationCategory,
        val expectedCpl: Int,
        val actualCp: Int? = null,
        val actualMate: Int? = null,
        val engineCp: Int? = null,
        val engineMate: Int? = null,
    ) {
        val expectedEngineCp: Int
            get() = engineCp ?: heuristicMateCp(engineMate)

        val expectedActualCp: Int
            get() = actualCp?.coerceIn(-MAX_ABS_CP, MAX_ABS_CP) ?: heuristicMateCp(actualMate)

        private fun heuristicMateCp(mate: Int?): Int {
            requireNotNull(mate)
            val bonus = (MAX_MATE - kotlin.math.abs(mate)).coerceAtLeast(0) * MATE_BONUS_PER_MOVE
            return if (mate < 0) -MAX_ABS_CP - bonus else MAX_ABS_CP + bonus
        }
    }
}
