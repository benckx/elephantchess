package io.elephantchess.servicelayer.utils

import io.elephantchess.model.MoveAnnotationCategory
import io.elephantchess.servicelayer.dto.analysis.MoveAnnotationDetails
import io.elephantchess.servicelayer.dto.analysis.MoveAnnotationResult
import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
import io.elephantchess.xiangqi.Board
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.xiangqi.Board.Companion.resetFullMoveCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoveAnnotationCalculationTest {
    private companion object {
        const val MATE_NEGATIVE_TWELVE_HEURISTIC_CP = -7_930
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
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.BLUNDER,
                expectedCpl = -2577,
                actualCp = 885,
                engineCp = -1692,
            ),
        )
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.BLUNDER,
                expectedCpl = -328,
                actualCp = 320,
                engineCp = -8,
            ),
        )
    }

    @Test
    fun `sample MISTAKE cases match sample data`() {
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.MISTAKE,
                expectedCpl = -122,
                actualCp = 190,
                engineCp = 68,
            ),
        )
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.MISTAKE,
                expectedCpl = -141,
                actualCp = 206,
                engineCp = 65,
            ),
        )
    }

    @Test
    fun `sample INACCURACY cases match sample data`() {
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.INACCURACY,
                expectedCpl = -66,
                actualCp = -1,
                engineCp = -67,
            ),
        )
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.INACCURACY,
                expectedCpl = -68,
                actualCp = 58,
                engineCp = -10,
            ),
        )
    }

    @Test
    fun `sample INTERESTING cases match sample data`() {
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.INTERESTING,
                expectedCpl = 65,
                actualCp = -2232,
                engineCp = -2167,
            ),
        )
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.INTERESTING,
                expectedCpl = 94,
                actualCp = -3901,
                engineCp = -3807,
            ),
        )
    }

    @Test
    fun `sample GOOD cases match sample data`() {
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.GOOD,
                expectedCpl = 180,
                actualCp = 606,
                engineCp = 786,
            ),
        )
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.GOOD,
                expectedCpl = 107,
                actualCp = -1013,
                engineCp = -906,
            ),
        )
    }

    @Test
    fun `sample BRILLIANT cases match sample data`() {
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.BRILLIANT,
                expectedCpl = 890,
                actualMate = -12,
                expectedActualMoveHeuristicCp = MATE_NEGATIVE_TWELVE_HEURISTIC_CP,
                engineCp = -7040,
            ),
        )
        assertMatchesSample(
            AnnotationTestCase(
                expectedCategory = MoveAnnotationCategory.BRILLIANT,
                expectedCpl = 312,
                actualCp = -2917,
                engineCp = -2605,
            ),
        )
    }

    private fun assertMatchesSample(case: AnnotationTestCase) {
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
                engineCp = case.engineCp,
                actualMoveCp = case.expectedActualMoveHeuristicCp ?: case.actualCp!!,
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

    private data class AnnotationTestCase(
        val expectedCategory: MoveAnnotationCategory,
        val expectedCpl: Int,
        val actualCp: Int? = null,
        val actualMate: Int? = null,
        val engineCp: Int,
        val expectedActualMoveHeuristicCp: Int? = null,
    ) {
        init {
            require((actualCp != null).xor(actualMate != null)) { "Exactly one of actualCp or actualMate must be provided" }
            require(actualCp != null || expectedActualMoveHeuristicCp != null) {
                "Mate-based cases must provide expectedActualMoveHeuristicCp"
            }
        }
    }
}
