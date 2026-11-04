package io.elephantchess.xiangqi.testutils

import kotlin.test.Test
import kotlin.test.assertTrue

class JanggiGameMovesDtoCacheTest {

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
