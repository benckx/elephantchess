package io.elephantchess.xiangqi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JanggiSetupTest {

    private val setups = mapOf(
        JanggiSetup.INNER_ELEPHANT to "rnba1abnr",
        JanggiSetup.OUTER_ELEPHANT to "rbna1anbr",
        JanggiSetup.RIGHT_ELEPHANT to "rnba1anbr",
        JanggiSetup.LEFT_ELEPHANT to "rbna1abnr",
        JanggiSetup.CENTRAL_CHARIOT to "bnra1arnb",
    )

    @Test
    fun setupsHaveTheSpecifiedLowercaseFenFragments() {
        assertEquals(JanggiSetup.entries.toSet(), setups.keys)
        setups.forEach { (setup, fen) -> assertEquals(fen, setup.fen) }
    }

    @Test
    fun defaultRemainsInnerElephantsOnBothSides() {
        assertEquals(JanggiBoard.DEFAULT_JANGGI_START_FEN, generateJanggiStartFen())
        assertEquals(JanggiBoard().outputFen(), generateJanggiStartFen())
    }

    @Test
    fun composesAllTwentyFiveIndependentSetupsWithBluePlayingFirst() {
        val fens = mutableSetOf<String>()
        setups.forEach { (blue, blueFen) ->
            setups.forEach { (red, redFen) ->
                val fen = generateJanggiStartFen(blue, red)
                assertEquals(
                    "$redFen/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/${blueFen.uppercase()} w - - 0 1",
                    fen,
                )
                val board = JanggiBoard(fen)
                assertEquals(fen, board.outputFen())
                assertEquals(Color.WHITE, board.colorToPlay())
                assertEquals(16, board.listAllPieces(Color.WHITE).size)
                assertEquals(16, board.listAllPieces(Color.BLACK).size)
                assertFalse(board.isInCheck(Color.WHITE))
                assertFalse(board.isInCheck(Color.BLACK))
                fens += fen
            }
        }
        assertEquals(25, fens.size)
    }

    @Test
    fun eitherSideCanKeepItsDefaultSetup() {
        assertEquals(
            generateJanggiStartFen(JanggiSetup.OUTER_ELEPHANT, JanggiSetup.INNER_ELEPHANT),
            generateJanggiStartFen(blue = JanggiSetup.OUTER_ELEPHANT),
        )
        assertEquals(
            generateJanggiStartFen(JanggiSetup.INNER_ELEPHANT, JanggiSetup.CENTRAL_CHARIOT),
            generateJanggiStartFen(red = JanggiSetup.CENTRAL_CHARIOT),
        )
    }
}
