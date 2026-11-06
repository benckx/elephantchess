package io.elephantchess.engines.process

import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.test.runTest
import org.mockito.Answers.CALLS_REAL_METHODS
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EngineSearchMovesTest {

    @Test
    fun `depth queries restrict root moves only when requested`() =
        runTest {
            val process = mock<EngineProcess>(defaultAnswer = CALLS_REAL_METHODS)
            doNothing().whenever(process).inputCommand(any())

            process.queryForBestMove("fen", 1, Variant.JANGGI, maxDelay = 0, searchMoves = listOf("e8d8", "a6a5"))
            verify(process).inputCommand("go depth 1 searchmoves e8d8 a6a5")

            process.queryForBestMove("fen", 2, maxDelay = 0)
            verify(process).inputCommand("go depth 2")

            assertFailsWith<IllegalArgumentException> {
                process.queryForBestMove("fen", 1, maxDelay = 0, searchMoves = emptyList())
            }
        }
}
