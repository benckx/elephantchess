package io.elephantchess.db.utils

import io.elephantchess.db.dao.codegen.tables.pojos.SevenKingdomsGame
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordOpsTest {

    @Test
    fun maxColorPerPlayerTest() {
        val game = SevenKingdomsGame()

        game.minPlayers = 2
        assertEquals(3, game.minColorPerPlayer())
        assertEquals(4, game.maxColorPerPlayer())

        game.minPlayers = 3
        assertEquals(2, game.minColorPerPlayer())
        assertEquals(3, game.maxColorPerPlayer())

        game.minPlayers = 4
        assertEquals(1, game.minColorPerPlayer())
        assertEquals(2, game.maxColorPerPlayer())

        game.minPlayers = 5
        assertEquals(1, game.minColorPerPlayer())
        assertEquals(2, game.maxColorPerPlayer())

        game.minPlayers = 6
        assertEquals(1, game.minColorPerPlayer())
        assertEquals(2, game.maxColorPerPlayer())

        game.minPlayers = 7
        assertEquals(1, game.minColorPerPlayer())
        assertEquals(1, game.maxColorPerPlayer())
    }

}
