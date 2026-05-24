package io.elephantchess.webapp.rendering

import io.elephantchess.model.Engine
import io.elephantchess.xiangqi.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class GamePageRendererTest {

    @Test
    fun `default titles remain backward compatible`() {
        assertEquals("Game", GamePageRenderer.DEFAULT_PVP_TITLE)
        assertEquals("Play vs. Bot", GamePageRenderer.DEFAULT_PVB_TITLE)
    }

    @Test
    fun `pvp page title contains both player names`() {
        assertEquals(
            "alice vs bob",
            GamePageRenderer.pvpPageTitle("alice", "bob")
        )
    }

    @Test
    fun `pvp page title uses fallback when invitee is missing`() {
        assertEquals(
            "alice vs opponent",
            GamePageRenderer.pvpPageTitle("alice", null)
        )
    }

    @Test
    fun `pvb page title puts human first when playing red`() {
        assertEquals(
            "alice vs Fairy Stockfish (5)",
            GamePageRenderer.pvbPageTitle("alice", Color.RED, Engine.FAIRYSTOCKFISH, 5)
        )
    }

    @Test
    fun `pvb page title puts bot first when human plays black`() {
        assertEquals(
            "Pikafish (12) vs alice",
            GamePageRenderer.pvbPageTitle("alice", Color.BLACK, Engine.PIKAFISH, 12)
        )
    }

    @Test
    fun `pvb page title uses anonymous fallback`() {
        assertEquals(
            "anonymous vs Pikafish (8)",
            GamePageRenderer.pvbPageTitle(null, Color.RED, Engine.PIKAFISH, 8)
        )
    }

}
