package io.elephantchess.utils

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

object EloCalculator {

    /**
     * https://medium.com/purple-theory/what-is-elo-rating-c4eb7a9061e0
     */
    fun calculateElo(winnerRating: Int, loserRating: Int, k: Int = 16): EloTransfer {
        val winnerExpectedScore = expectedScore(winnerRating, loserRating)
        val loserExpectedScore = expectedScore(loserRating, winnerRating)
        val winnerNewRating = winnerRating + (k * (1 - winnerExpectedScore))
        val loserNewRating = loserRating + (k * (0 - loserExpectedScore))
        return EloTransfer(ceil(winnerNewRating).toInt(), floor(loserNewRating).toInt())
    }

    private fun expectedScore(rating: Int, otherPlayerRating: Int): Double {
        return 1 / (1 + 10.0.pow((otherPlayerRating.toDouble() - rating.toDouble()) / 400))
    }

}
