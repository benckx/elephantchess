package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.AbstractPieceType.CHARIOT
import io.elephantchess.xiangqi.Color.RED
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.Position.Companion.parsePositionFromUci
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveHistoryTest {

    private val logger = KotlinLogging.logger {}

    /**
     * Bugfix for perpetual check detection.
     *
     * https://github.com/benckx/elephantchess/issues/379
     * https://elephantchess.io/game?id=ctE1ItdFpFPE
     */
    @Test
    fun perpetualCheckTest01() {
        val moves = listOf(
            "b0c2", "b9c7", "c3c4", "h9i7", "g3g4", "a9a8", "h0g2", "a8d8", "g2f4", "h7h4", "i3i4", "h4f4", "h2e2",
            "f4f2", "i0h0", "f2c2", "c4c5", "c6c5", "b2b4", "d8d3", "b4e4", "d9e8", "a0b0", "d3e3", "e4c4", "b7a7",
            "c4c7", "c2c7", "b0b9", "c7c0", "e0e1", "g9e7", "g4g5", "i9h9", "h0h9", "i7h9", "g5g6", "h9i7", "g6g7",
            "i7g6", "g7g8", "g6f4", "g8g9", "e7g9", "b9c9", "e8d9", "e1d1", "f9e8", "c9c5", "c0f0", "c5c6", "e3d3",
            "d1e1", "f4g2", "e1e0", "a7a3", "e2e8", "d9e8", "c6e6", "a3a0", "d0e1", "f0f4", "e6a6", "f4e4", "e1f0",
            "d3e3", "e0d0", "e3d3", "d0e0", "a0f0", "a6a9", "e8d9", "a9a4", "d3e3", "e0d0", "f0f4", "a4a2", "e3d3",
            "d0e0", "d3e3", "e0d0", "g2e1", "d0d1", "e1g0", "a2g2", "e3e1", "d1d2", "g0i1", "g2g9", "e9e8", "g9d9",
            "e4i4", "d9d8", "e8e7", "d8d7", "e7e8", "d7d8", "e8e7", "d8d7", "e7e8", "d7d8", "e8e7", "d8d7", "e7e8",
            "d7d8", "e8e7", "d8d7", "e7e8", "d7d8", "e8e7", "d8d7", "e7e8"
        )

        val board = Board(keepHistory = true)
        board.registerMoves(moves.map { parseMoveFromUci(it) })
        val history = board.getHistory()!!
        val checkingPiece = PhysicalPiece(PieceType(CHARIOT, RED), parsePositionFromUci("a0"))
        val sequenceOfChecks = history.findSequencesOfConsecutiveChecks(RED, 1)[1]!!

        sequenceOfChecks.moves.forEach { historicalMove ->
            logger.debug { historicalMove }
        }

        assertEquals(10, sequenceOfChecks.moves.size)
        assertEquals(47, sequenceOfChecks.moves.first().fullMove())
        assertEquals(56, sequenceOfChecks.moves.last().fullMove())
        assertEquals(setOf(checkingPiece), sequenceOfChecks.attackers)
        assertTrue(sequenceOfChecks.exceeds(PerpetualCheckingRule(1, 6)))
    }

}
