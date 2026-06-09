package io.elephantchess.servicelayer.services

import io.elephantchess.servicelayer.services.PuzzleSolvabilityValidator.hasEnoughMovesAtEachPlayerStep
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PuzzleSolvabilityValidatorTest {

    private val defaultStartFen = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 0"

    // red has only its general (2 legal moves), black general on a different file
    private val loneGeneralFen = "5k3/9/9/9/9/9/9/9/9/3K5 w - - 0 0"

    @Test
    fun `puzzle is valid when the player has enough moves at each step`() {
        assertTrue(
            hasEnoughMovesAtEachPlayerStep(
                startFen = defaultStartFen,
                setupMoves = emptyList(),
                solutionMoves = listOf("h2e2"),
            )
        )
    }

    @Test
    fun `puzzle is invalid when the player has too few moves at a step`() {
        assertFalse(
            hasEnoughMovesAtEachPlayerStep(
                startFen = loneGeneralFen,
                setupMoves = emptyList(),
                solutionMoves = listOf("d0d1"),
            )
        )
    }

    @Test
    fun `only the player turns are checked, opponent turns are skipped`() {
        // the opponent move is not subject to the minimum-moves check
        assertTrue(
            hasEnoughMovesAtEachPlayerStep(
                startFen = defaultStartFen,
                setupMoves = emptyList(),
                solutionMoves = listOf("h2e2", "h9g7"),
            )
        )
    }

    @Test
    fun `minimum number of moves threshold is configurable`() {
        assertFalse(
            hasEnoughMovesAtEachPlayerStep(
                startFen = defaultStartFen,
                setupMoves = emptyList(),
                solutionMoves = listOf("h2e2"),
                minLegalMoves = 1000,
            )
        )
    }

    @Test
    fun `empty solution is considered valid`() {
        assertTrue(
            hasEnoughMovesAtEachPlayerStep(
                startFen = defaultStartFen,
                setupMoves = emptyList(),
                solutionMoves = emptyList(),
            )
        )
    }

}
