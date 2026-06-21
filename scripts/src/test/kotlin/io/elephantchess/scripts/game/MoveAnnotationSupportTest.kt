package io.elephantchess.scripts.game

import io.elephantchess.servicelayer.dto.engines.InfoLineResultDto
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

    private fun infoLine(cp: Int? = null, mate: Int? = null, isCheckmate: Boolean = false) = InfoLineResultDto(
        line = null,
        fen = "fen",
        depth = 20,
        cp = cp,
        mate = mate,
        pv = emptyList(),
        bestMove = null,
        isCheckmate = isCheckmate,
    )
}
