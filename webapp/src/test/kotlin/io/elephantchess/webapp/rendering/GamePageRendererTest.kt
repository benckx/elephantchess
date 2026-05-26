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
            "alice vs. bob",
            GamePageRenderer.formatPvpPageTitle("alice", "bob", Color.RED)
        )
    }

    @Test
    fun `pvp page title keeps red first when inviter is black`() {
        assertEquals(
            "bob vs. alice",
            GamePageRenderer.formatPvpPageTitle("alice", "bob", Color.BLACK)
        )
    }

    @Test
    fun `pvp page title uses opponent fallback with red first when invitee is missing`() {
        assertEquals(
            "<waiting> vs. alice",
            GamePageRenderer.formatPvpPageTitle("alice", null, Color.BLACK)
        )
    }

    @Test
    fun `pvp page title uses new game label when colors are not picked yet`() {
        assertEquals(
            "new game by alice",
            GamePageRenderer.formatPvpPageTitle("alice", null, null)
        )
    }

    @Test
    fun `pvb page title puts human first when playing red`() {
        assertEquals(
            "alice vs. Fairy Stockfish (5)",
            GamePageRenderer.formatPvbPageTitle("alice", Color.RED, Engine.FAIRYSTOCKFISH, 5)
        )
    }

    @Test
    fun `pvb page title puts bot first when human plays black`() {
        assertEquals(
            "Pikafish (12) vs. alice",
            GamePageRenderer.formatPvbPageTitle("alice", Color.BLACK, Engine.PIKAFISH, 12)
        )
    }

    @Test
    fun `pvb page title uses anonymous fallback`() {
        assertEquals(
            "anonymous vs. Pikafish (8)",
            GamePageRenderer.formatPvbPageTitle(null, Color.RED, Engine.PIKAFISH, 8)
        )
    }

}
