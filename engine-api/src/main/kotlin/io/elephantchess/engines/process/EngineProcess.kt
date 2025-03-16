package io.elephantchess.engines.process

import io.elephantchess.engines.protocol.FirstMatchLineListener
import io.elephantchess.engines.protocol.InfoLineListener
import io.elephantchess.engines.protocol.LineListener
import io.elephantchess.engines.protocol.commands.EngineProcessLocator
import io.elephantchess.engines.protocol.model.InfoLinesResult
import io.elephantchess.engines.utils.EngineUtils.waitForCondition
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the system calls to the actual engine process
 * (sends commands and listen for responses on the output channel).
 */
abstract class EngineProcess(
    private val locator: EngineProcessLocator,
    val engineId: EngineId,
    val version: String?,
) : Runnable {

    abstract val logger: KLogger

    private lateinit var process: Process
    private lateinit var input: BufferedWriter
    private lateinit var reader: BufferedReader

    private val processedHasStarted = AtomicBoolean(false)
    private var hasQuit = false
    private var lineListener: LineListener? = null

    open fun initEngine() {}

    fun inputCommand(command: String) {
        logger.debug { "sending to engine: $command" }
        input.write(command + "\n")
        input.flush()
    }

    suspend fun queryForBestMove(
        fen: String,
        maxDepth: Int,
        maxDelay: Long = DEFAULT_MAX_DELAY,
        checkPeriod: Long = DEFAULT_CHECK_PERIOD,
    ): InfoLinesResult {
        val infoListener = InfoLineListener()
        lineListener = infoListener

        inputCommand("position fen $fen")
        inputCommand("go depth $maxDepth")
        waitForCondition(maxDelay, checkPeriod) {
            infoListener.shouldStopSearch(maxDepth)
        }
        inputCommand("stop")
        return infoListener.getResult()
    }

    fun inputCommandAndWaitBlocking(
        command: String,
        maxDelay: Long = DEFAULT_MAX_DELAY,
        checkPeriod: Long = DEFAULT_CHECK_PERIOD,
        linePredicate: (String) -> Boolean,
    ): String {
        val matchListener = FirstMatchLineListener(linePredicate)
        lineListener = matchListener
        inputCommand(command)
        runBlocking {
            waitForCondition(maxDelay, checkPeriod) {
                matchListener.hasMatched()
            }
        }
        lineListener = null
        return matchListener.matchingLine!!
    }

    fun quit(timeoutMs: Long = 10_000): Boolean {
        hasQuit = true
        inputCommand("quit")
        return process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    }

    override fun run() {
        fun receivedLine(line: String) {
            logger.debug { line }
            lineListener?.receivedLine(line)
        }

        val launchCommand = locator.launchCommand(engineId.pathOfExecutable(version))
        logger.info { "running ${engineId.displayName} engine, launching $launchCommand" }
        process = ProcessBuilder(launchCommand.split(" ")).start()
        input = process.outputStream.bufferedWriter()
        reader = process.inputStream.bufferedReader()
        processedHasStarted.set(true)
        initEngine()

        while (!hasQuit) {
            reader.readLine()?.let { receivedLine(it) }
        }
    }

    fun waitUntilReadyBlocking(maxDelay: Long = DEFAULT_MAX_DELAY, checkPeriod: Long = DEFAULT_CHECK_PERIOD) {
        val hasStarted = runBlocking {
            waitForCondition(maxDelay, checkPeriod) { processedHasStarted.get() }
        }

        if (hasStarted) {
            logger.info { "$engineId process has started" }
            inputCommandAndWaitBlocking("isready", maxDelay, checkPeriod) { line -> line == "readyok" }
            logger.info { "$engineId process is ready" }
        } else {
            throw Exception("$engineId process could not start")
        }
    }

    companion object {

        const val DEFAULT_MAX_DELAY = 5_000L
        const val DEFAULT_CHECK_PERIOD = 100L

    }

}
