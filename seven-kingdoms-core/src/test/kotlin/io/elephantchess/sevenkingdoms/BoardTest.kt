package io.elephantchess.sevenkingdoms

import io.elechantchess.sevenkingdoms.testutils.GameEntryCacheManager.getAllGames
import io.elephantchess.sevenkingdoms.AbstractPieceType.GENERAL
import io.elephantchess.sevenkingdoms.Board.Companion.CAPTURED_KINGDOMS_THRESHOLD_TO_WIN
import io.elephantchess.sevenkingdoms.Board.Companion.CAPTURE_THRESHOLD_TO_WIN
import io.elephantchess.sevenkingdoms.Board.Companion.DEFAULT_START_FEN
import io.elephantchess.sevenkingdoms.Color.*
import io.elephantchess.sevenkingdoms.VictoryType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardTest {

    @Test
    fun testLoadAndOutputFenTest() {
        val board = Board()
        assertEquals(DEFAULT_START_FEN, board.outputFen())
    }

    @Test
    fun defaultBoardTest() {
        val board = Board()
        assertEquals(WHITE, board.colorToPlay())
        assertEquals(null, board.winner())
        assertEquals(board.captures().size, Color.entries.size)
        assertEquals(board.capturedKingdomsMap().size, Color.entries.size)
        assertTrue(board.listCaptureEvents().isEmpty())
        assertEquals(board.listLegalMovesFor(WHITE).toSet(), board.listCurrentLegalMoves().toSet())

        board.listCurrentLegalMoves().forEach { move ->
            assertEquals(WHITE, board.pieceAt(move.from)!!.color)
            board.copy().registerMove(move) // doesn't throw Exception
        }
        board.listLegalMovesFor(WHITE).forEach { move ->
            assertEquals(WHITE, board.pieceAt(move.from)!!.color)
        }

        val legalMovesForAllColors = Color.entries.flatMap { board.listLegalMovesFor(it) }
        assertEquals(legalMovesForAllColors.toSet(), board.listAllLegalMoves().toSet())
    }

    @Test
    fun testDataIsCorrectTest() {
        getAllGames().forEach { game ->
            val board = game.toBoard()
            assertEquals(game.winner, board.winner())
            assertEquals(game.victoryType, board.victoryType())
            assertEquals(game.capturedKingdoms, board.capturedKingdomsMap())
            assertEquals(game.colorsStillInGame, board.colorsStillInGame())
            assertFalse(board.allEliminatedColors().contains(game.winner))

            board.allEliminatedColors().forEach { color ->
                assertEquals(0, board.listPiecesByColor(color).size)
            }

            when (game.victoryType) {
                LAST_KINGDOM_REMAINING -> {
                    assertEquals(1, board.colorsStillInGame().size)
                    assertEquals(board.winner(), board.colorsStillInGame().first())
                    assertEquals(6, board.listCaptureEvents().size)
                }

                CAPTURED_ENOUGH_PIECES -> {
                    val captured = board.captures()[game.winner]!!
                    assertTrue(captured.size >= CAPTURE_THRESHOLD_TO_WIN)
                }

                CAPTURED_ENOUGH_KINGDOMS -> {
                    assertEquals(CAPTURED_KINGDOMS_THRESHOLD_TO_WIN, board.capturedKingdomsMap()[game.winner]!!.size)
                }
            }
        }
    }

    @Test
    fun extraEliminationsTest01() {
        val board = Board()
        val event1 = ExtraEliminationEvent(20, listOf(ORANGE, BLUE))
        val event2 = ExtraEliminationEvent(30, listOf(PURPLE, BLACK))
        board.setExtraEliminationEvents(listOf(event1, event2))

        // before elimination events apply,
        // the colors are still playing
        var encounteredEventColors1 = false
        var encounteredEventColors2 = false

        repeat(30) {
            val moves = board.listCurrentLegalMovesMinusGeneralCaptures()
            board.registerMove(moves.random())

            if (event1.colors.contains(board.colorToPlay())) {
                encounteredEventColors1 = true
            }
            if (event2.colors.contains(board.colorToPlay())) {
                encounteredEventColors2 = true
            }
        }

        assertTrue(encounteredEventColors1)
        assertTrue(encounteredEventColors2)

        // after elimination events apply,
        // colors are not playing anymore
        encounteredEventColors1 = false
        encounteredEventColors2 = false

        while (board.listHistoricalMoves().size < 60) {
            val moves = board.listCurrentLegalMovesMinusGeneralCaptures()
            board.registerMove(moves.random())

            if (event1.colors.contains(board.colorToPlay())) {
                encounteredEventColors1 = true
            }
            if (event2.colors.contains(board.colorToPlay())) {
                encounteredEventColors2 = true
            }
        }

        assertFalse(encounteredEventColors1)
        assertFalse(encounteredEventColors2)
    }

    // filtering out general captures so the game doesn't end too soon
    private fun Board.listCurrentLegalMovesMinusGeneralCaptures(): List<Move> {
        val allGenerals = listAllPieces().filter { it.abstractPieceType == GENERAL }
        return listCurrentLegalMoves().filterNot { move -> allGenerals.any { it.position == move.to } }
    }

}
