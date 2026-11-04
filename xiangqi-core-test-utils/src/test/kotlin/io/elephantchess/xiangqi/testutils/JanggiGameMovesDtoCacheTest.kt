package io.elephantchess.xiangqi.testutils

import kotlin.test.Test
import kotlin.test.assertTrue

class JanggiGameMovesDtoCacheTest {

    @Test
    fun loadsGeneratedGames() {
        val games = JanggiGameMovesDtoCache().listAll()
        assertTrue(games.size == 200)
        assertTrue(games.distinctBy { it.gameId }.size == 200)
        assertTrue(games.distinctBy { it.uciMoves.joinToString(",") }.size == 200)
        assertTrue(games.all { game -> game.gameId.isNotBlank() && game.uciMoves.isNotEmpty() })
    }
}
