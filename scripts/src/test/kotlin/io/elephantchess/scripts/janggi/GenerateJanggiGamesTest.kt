package io.elephantchess.scripts.janggi

import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.FairyStockfishEngineId
import io.elephantchess.engines.protocol.model.InfoLineResult
import io.elephantchess.engines.protocol.model.InfoLinesResult
import io.elephantchess.xiangqi.JanggiBoard.Companion.DEFAULT_JANGGI_START_FEN
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenerateJanggiGamesTest {

    @Test
    fun `uses Janggi and full move history until checkmate`() =
        runTest {
            val engines = mock<EnginePool>()
            val positions = mutableListOf<String>()
            whenever(
                engines.queryForDepth(
                    any(),
                    eq(FairyStockfishEngineId),
                    any(),
                    any(),
                    eq(Variant.JANGGI),
                    isNull(),
                    eq(10L),
                    eq(true),
                ),
            )
                .thenAnswer { invocation ->
                    val fen = invocation.getArgument<String>(0)
                    positions += fen
                    val moves = fen.substringAfter(" moves ").split(" ").filter { it.isNotEmpty() }
                    if (moves.size == 8) {
                        InfoLinesResult(listOf(InfoLineResult(null, null, mate = 0, line = "")), true, null, null)
                    } else {
                        InfoLinesResult(emptyList(), false, "a${moves.size}a${moves.size + 1}", null)
                    }
                }

            val game = GenerateJanggiGames.generateSelfPlayGame(engines, maxMoves = 20, maxDepth = 1)

            assertEquals(8, game.moves.size)
            assertEquals(GenerateJanggiGames.GameResult.CHECKMATE, game.result)
            assertTrue(positions.all { it.startsWith("$DEFAULT_JANGGI_START_FEN moves ") })
            assertEquals("$DEFAULT_JANGGI_START_FEN moves ${game.moves.joinToString(" ")}", positions.last())
        }

    @Test
    fun `stops at move limit and records a draw`() =
        runTest {
            val engines = mock<EnginePool>()
            whenever(
                engines.queryForDepth(
                    any(),
                    eq(FairyStockfishEngineId),
                    any(),
                    any(),
                    eq(Variant.JANGGI),
                    isNull(),
                    eq(10L),
                    eq(true),
                ),
            )
                .thenReturn(InfoLinesResult(emptyList(), false, "a0a1", null))

            val game = GenerateJanggiGames.generateSelfPlayGame(engines, maxMoves = 2, maxDepth = 1)

            assertEquals(listOf("a0a1", "a0a1"), game.moves)
            assertEquals(GenerateJanggiGames.GameResult.DRAW, game.result)
        }

    @Test
    fun `fails rather than treating an incomplete engine result as a draw`() =
        runTest {
            val engines = mock<EnginePool>()
            whenever(
                engines.queryForDepth(
                    any(),
                    eq(FairyStockfishEngineId),
                    any(),
                    any(),
                    eq(Variant.JANGGI),
                    isNull(),
                    eq(10L),
                    eq(true),
                ),
            )
                .thenReturn(InfoLinesResult(emptyList(), false, null, null))

            assertFailsWith<IllegalStateException> {
                GenerateJanggiGames.generateSelfPlayGame(engines, maxMoves = 2, maxDepth = 1)
            }
        }
}
