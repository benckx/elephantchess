package io.elephantchess.utils

import io.elephantchess.utils.EloCalculator.calculateElo
import kotlin.test.Test
import kotlin.test.assertEquals

class EloCalculatorTest {

    @Test
    fun strongerPlayerWins() {
        val newRatings = calculateElo(2600, 2300)
        assertEquals(2603, newRatings.winnerNewRating)
        assertEquals(2297, newRatings.loserNewRating)
    }

    @Test
    fun strongerPlayerLoses() {
        val newRatings = calculateElo(2300, 2600)
        assertEquals(2314, newRatings.winnerNewRating)
        assertEquals(2586, newRatings.loserNewRating)
    }

    // https://github.com/benckx/elephantchess.io/issues/25
    @Test
    fun roundedUp() {
        val newRatings = calculateElo(1409, 800)
        assertEquals(1410, newRatings.winnerNewRating)
        assertEquals(799, newRatings.loserNewRating)
    }

}
