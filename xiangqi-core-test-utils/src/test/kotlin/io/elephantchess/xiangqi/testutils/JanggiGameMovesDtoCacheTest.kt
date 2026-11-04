package io.elephantchess.xiangqi.testutils

import kotlin.test.Test
import kotlin.test.assertTrue

class JanggiGameMovesDtoCacheTest {

    @Test
    fun loadsGeneratedGames() {
        val games = JanggiGameMovesDtoCache().listAll()
        assertTrue(games.isNotEmpty())
        assertTrue(games.all { game -> game.gameId.isNotBlank() && game.uciMoves.isNotEmpty() })
    }
}
