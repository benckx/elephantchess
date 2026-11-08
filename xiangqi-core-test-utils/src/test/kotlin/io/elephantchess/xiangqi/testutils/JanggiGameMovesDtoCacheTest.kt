package io.elephantchess.xiangqi.testutils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JanggiGameMovesDtoCacheTest {

    @Test
    fun usesZeroBasedCoordinates() {
        val games = JanggiGameMovesDtoCache().listAll()
        val movePattern = Regex("[a-i][0-9][a-i][0-9]")
        assertTrue(games.all { game -> game.uciMoves.all { movePattern.matches(it) } })
        assertTrue(games.any { game -> game.uciMoves.any { it.contains('0') } })
        val rawMoves = ResourcesUtils.getResourceAsText("/janggi.txt")
            .lineSequence().first { it.isNotBlank() }.split(";")[1].split(",")
        assertEquals(rawMoves, games.first().uciMoves)
    }

    @Test
    fun loadsGeneratedGames() {
        val games = JanggiGameMovesDtoCache().listAll()
        assertEquals(200, games.size)
        assertEquals(200, games.distinctBy { it.gameId }.size)
        assertEquals(200, games.distinctBy { Triple(it.blueSetup, it.redSetup, it.uciMoves) }.size)
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

    @Test
    fun loadsIndependentSetups() {
        val game = JanggiGameMovesDtoCache.parseLine("setup;e3d3;DRAW;OUTER_ELEPHANT;CENTRAL_CHARIOT")
        assertEquals("OUTER_ELEPHANT", game.blueSetup)
        assertEquals("CENTRAL_CHARIOT", game.redSetup)
        assertEquals("DRAW", game.outcome)
        assertEquals(listOf("e3d3"), game.uciMoves)
    }

    @Test
    fun legacyFixturesUseInnerElephants() {
        val game = JanggiGameMovesDtoCache.parseLine("legacy;e3d3;DRAW")
        assertEquals("INNER_ELEPHANT", game.blueSetup)
        assertEquals("INNER_ELEPHANT", game.redSetup)
    }

    @Test
    fun rejectsInvalidSetupColumns() {
        assertFailsWith<IllegalArgumentException> {
            JanggiGameMovesDtoCache.parseLine("bad;e3d3;DRAW;INNER_ELEPHANT")
        }
        assertFailsWith<IllegalArgumentException> {
            JanggiGameMovesDtoCache.parseLine("bad;e3d3;DRAW;;INNER_ELEPHANT")
        }
    }
}
