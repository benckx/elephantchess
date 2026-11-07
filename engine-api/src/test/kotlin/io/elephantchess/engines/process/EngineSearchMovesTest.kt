package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.commands.EngineProcessLocator
import io.elephantchess.xiangqi.Variant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.io.TempDir
import org.mockito.Answers.CALLS_REAL_METHODS
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

    @Test
    fun `waiting for a best move fails if the engine does not respond to stop`() =
        runTest {
            val process = mock<EngineProcess>(defaultAnswer = CALLS_REAL_METHODS)
            doNothing().whenever(process).inputCommand(any())

            assertFailsWith<IllegalStateException> {
                process.queryForBestMove("fen", 1, maxDelay = 0, checkPeriod = 10, waitForBestMove = true)
            }
            verify(process).inputCommand("stop")
        }

    @Test
    fun `waits for best move after depth info and supports paths with spaces`(@TempDir tempDir: Path) {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val executable = tempDir.resolve("fake engine").toFile()
        executable.writeText(
            """
            #!/bin/sh
            while IFS= read -r line; do
              case "${'$'}line" in
                isready) echo readyok ;;
                "go depth "*) echo "info depth 1 time 1 score cp 1 pv a0a1"; sleep 0.1; echo "bestmove a0a1" ;;
                quit) exit ;;
              esac
            done
            """.trimIndent(),
        )
        assertTrue(executable.setExecutable(true))
        val locator = object : EngineProcessLocator {
            override fun launchCommand(binFileName: String) = executable.path
            override fun launchArguments(binFileName: String) = listOf(executable.path)
        }
        val process = FairyStockfishEngineProcess(locator, "test", 1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit(process)
            process.waitUntilReadyBlocking()
            val result = runBlocking {
                process.queryForBestMove("fen", 1, maxDelay = 2_000, checkPeriod = 10, waitForBestMove = true)
            }
            assertEquals("a0a1", result.bestMove)
        } finally {
            process.quit()
            executor.shutdown()
        }
    }
}
