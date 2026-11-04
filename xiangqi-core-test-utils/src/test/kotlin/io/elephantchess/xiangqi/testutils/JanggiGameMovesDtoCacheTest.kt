package io.elephantchess.xiangqi.testutils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JanggiGameMovesDtoCacheTest {

    @Test
    fun normalizesFairyStockfishRanksToCoreCoordinates() {
        assertEquals(
            listOf("e3f3", "e6d6", "g0e3", "i6h6", "b0c2", "b9c7", "e3g6", "e8e8"),
            JanggiGameMovesDtoCache().findByGameId("1734e167").uciMoves,
        )
        val coreMovePattern = Regex("[a-i][0-9][a-i][0-9]")
        assertTrue(JanggiGameMovesDtoCache().listAll().all { game -> game.uciMoves.all { coreMovePattern.matches(it) } })
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
