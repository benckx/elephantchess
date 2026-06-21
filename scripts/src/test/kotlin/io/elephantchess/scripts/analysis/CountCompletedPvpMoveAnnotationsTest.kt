package io.elephantchess.scripts.analysis

import kotlin.test.Test
import kotlin.test.assertEquals

class CountCompletedPvpMoveAnnotationsTest {

    @Test
    fun formatPercentagesShowsAnnotatedAndGlobalShares() {
        val percentages = CountCompletedPvpMoveAnnotations.formatPercentages(
            count = 3,
            annotatedMoves = 4,
            totalMoves = 10,
        )

        assertEquals("75.0%", percentages.annotated)
        assertEquals("30.0%", percentages.global)
    }

    @Test
    fun formatPercentagesUsesDashWhenNoDenominatorExists() {
        val percentages = CountCompletedPvpMoveAnnotations.formatPercentages(
            count = 0,
            annotatedMoves = 0,
            totalMoves = 0,
        )

        assertEquals("-", percentages.annotated)
        assertEquals("-", percentages.global)
    }
}
