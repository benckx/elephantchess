package io.elephantchess.xiangqi

import io.elephantchess.xiangqi.testutils.ResourcesUtils.loadDigitsStringGame
import io.elephantchess.xiangqi.HalfMove.Companion.parseMoveFromUci
import io.elephantchess.xiangqi.HalfMove.Companion.parseMovesFromDigitsString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.test.Test
import kotlin.test.assertEquals

class HalfMoveTest {

    private val logger = KotlinLogging.logger {}

    @Test
    fun parseMoveFromUciTest01() {
        val uci = "h7d7"
        val move = parseMoveFromUci(uci)
        assertEquals("h7", move.from.toUci())
        assertEquals("d7", move.to.toUci())
        assertEquals("h8d8", move.toAlgebraic())
    }

    @Test
    fun parseMovesFromDigitsStringTest01() {
        val digits = loadDigitsStringGame("game1.ubb")
        val expectedFen = "3ak1b2/1C2a3n/2Nrb4/2R3P1p/9/4P3P/p2r5/4B4/3pA4/2B1KA3 w - - 0 57"
        val board = Board()
        board.registerMoves(parseMovesFromDigitsString(digits))
        val actualFen = board.outputFen()

        logger.info { "digits: $digits" }
        logger.info { "expected fen: $expectedFen" }
        logger.info { "actual fen:   $actualFen" }
        assertEquals(expectedFen, actualFen)
    }

}
