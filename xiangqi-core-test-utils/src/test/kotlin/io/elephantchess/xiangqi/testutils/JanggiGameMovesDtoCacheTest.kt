package io.elephantchess.xiangqi.testutils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JanggiGameMovesDtoCacheTest {

    @Test
    fun usesZeroBasedCoordinates() {
        val games = JanggiGameMovesDtoCache().listAll()
        val movePattern = Regex("[a-i][0-9][a-i][0-9]")
        assertTrue(games.all { game -> game.uciMoves.all { movePattern.matches(it) } })
        assertTrue(games.any { game -> game.uciMoves.any { it.contains('0') } })
        assertEquals(
            listOf("e3d3", "i6h6", "b0c2", "b9c7", "g0e3", "c6d6", "b2e2", "e8e9", "e3g6"),
            JanggiGameMovesDtoCache().findByGameId("7be97705").uciMoves,
        )
    }

    @Test
    fun loadsGeneratedGames() {
        val games = JanggiGameMovesDtoCache().listAll()
        assertTrue(games.size == 100)
        assertTrue(games.distinctBy { it.gameId }.size == 100)
        assertTrue(games.distinctBy { it.uciMoves.joinToString(",") }.size == 100)
        assertTrue(games.count { it.outcome == "CHECKMATE" } >= 25)
        assertTrue(
            games.all { game ->
                game.gameId.isNotBlank() &&
                    game.uciMoves.isNotEmpty() &&
                    game.outcome != null &&
                    game.outcome in setOf("CHECKMATE", "STALEMATE", "DRAW")
            },
        )
    }
}
