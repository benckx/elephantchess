package io.elephantchess.engines

import io.elephantchess.engines.process.*
import io.elephantchess.engines.protocol.commands.EngineProcessLocator
import io.elephantchess.engines.protocol.commands.LocalProcessLocator
import io.elephantchess.engines.protocol.model.InfoLinesResult
import io.elephantchess.engines.utils.EngineUtils.waitForCondition
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ExecutorService

/**
 * Coroutine-safe pool of engine processes.
 * Allow multiple users to use the same engine process with different positions.
 * For example, multiple users can play against the bot with different depths. Their queries are "queued".
 */
class EnginePool(
    configMap: Map<EngineId, EngineConfig>,
    private val executor: ExecutorService,
    engineProcessLocator: EngineProcessLocator = LocalProcessLocator,
) {

    private val logger = KotlinLogging.logger {}

    private val acquisitionMutex = Mutex()
    private val engineProcesses: List<LockableEngineProcess>

    init {
        fun createProcesses(): List<EngineProcess> {
            val result = mutableListOf<EngineProcess>()
            configMap.forEach { (engine, config) ->
                result += (0 until config.poolSize).map { engine.makeProcess(config, engineProcessLocator) }
            }

            return result.toList()
        }

        engineProcesses = createProcesses().map { process ->
            executor.submit(process)
            process.waitUntilReadyBlocking(10_000)
            SimpleLockableEngineProcess(process)
        }
    }

    suspend fun queryForDepth(fen: String, engineId: EngineId, depth: Int, timeout: Long = 20_000): InfoLinesResult? {
        return acquireAndExecute(engineId, timeout) { lockableEngineProcess ->
            lockableEngineProcess.queryForBestMove(fen, depth)
        }
    }

    private suspend fun <T> acquireAndExecute(
        engineId: EngineId,
        timeout: Long,
        block: suspend (LockableEngineProcess) -> T?,
    ): T? {
        suspend fun acquireEngineProcess(engineId: EngineId, timeout: Long): LockableEngineProcess? {
            acquisitionMutex.withLock {
                var acquiredProcess: LockableEngineProcess? = null
                waitForCondition(timeout, 20) {
                    acquiredProcess =
                        engineProcesses.firstOrNull { it.isFree() && it.engineProcess.engineId == engineId }
                    acquiredProcess != null
                }
                acquiredProcess?.lock()

                if (acquiredProcess == null) {
                    logger.warn { "acquireEngineProcess timed out after $timeout ms." }
                }

                return acquiredProcess
            }
        }

        suspend fun <T> executeOnEngine(
            lockableEngineProcess: LockableEngineProcess,
            block: suspend (LockableEngineProcess) -> T?,
        ): T? {
            try {
                return block(lockableEngineProcess)
            } catch (e: Exception) {
                logger.error(e) { "error while using engine" }
                return null
            } finally {
                lockableEngineProcess.unlock()
            }
        }

        return acquireEngineProcess(engineId, timeout)
            ?.let { executeOnEngine(it) { block(it) } }
    }

    fun close() {
        engineProcesses.forEach { engine -> engine.close() }
        executor.shutdown()
    }

    /**
     * Engine processes are in theory stateless, except that we need one command to input the position and one input to query the best move.
     * So we lock the process for a given query (e.g. best move at depth 8 given a position) and unlock it after the query is done.
     */
    private abstract class LockableEngineProcess(val engineProcess: EngineProcess) {

        abstract fun isFree(): Boolean
        abstract fun lock()
        abstract fun unlock()

        fun close() {
            engineProcess.quit()
        }

        suspend fun queryForBestMove(fen: String, depth: Int): InfoLinesResult {
            return engineProcess.queryForBestMove(fen, depth)
        }

    }

    private class SimpleLockableEngineProcess(engineProcess: EngineProcess) : LockableEngineProcess(engineProcess) {

        private var isFree: Boolean = true

        override fun isFree() = isFree

        override fun lock() {
            isFree = false
        }

        override fun unlock() {
            isFree = true
        }

    }

}
