package io.elephantchess.servicelayer.utils.ops

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Default error handler: logs the exception at error level.
 */
private val logError: suspend (Throwable) -> Unit = { throwable ->
    logger.error(throwable) { "error in periodic coroutine task" }
}

/**
 * Launches a coroutine that runs a suspend action at a fixed rate.
 * Returns a Job that can be cancelled to stop the periodic execution.
 *
 * @param scope The coroutine scope to launch in
 * @param period The time between consecutive executions
 * @param initialDelay The time to wait before the first execution (default: 0)
 * @param onError Callback invoked with any non-cancellation exception thrown by [action]
 * (e.g. to log and/or persist it). Defaults to logging the exception. Failures within the
 * callback itself are logged and swallowed so the periodic loop keeps running.
 * @param action The suspend function to execute periodically
 */
fun launchAtFixedRate(
    scope: CoroutineScope,
    period: Duration,
    initialDelay: Duration = Duration.ZERO,
    onError: suspend (Throwable) -> Unit = logError,
    action: suspend () -> Unit
): Job {
    return scope.launch {
        delay(initialDelay)

        while (isActive) {
            try {
                action()
            } catch (e: CancellationException) {
                throw e // Re-throw to properly cancel the coroutine
            } catch (e: Exception) {
                try {
                    onError(e)
                } catch (handlerError: Exception) {
                    logger.error(handlerError) { "error in periodic coroutine onError handler" }
                }
            }

            delay(period)
        }
    }
}

/**
 * Launches a coroutine that runs a suspend action at a fixed rate, starting immediately.
 * Returns a Job that can be cancelled to stop the periodic execution.
 *
 * @param scope The coroutine scope to launch in
 * @param period The time between consecutive executions
 * @param onError Callback invoked with any non-cancellation exception thrown by [action].
 * Defaults to logging the exception.
 * @param action The suspend function to execute periodically
 */
fun launchAtFixedRateStartImmediately(
    scope: CoroutineScope,
    period: Duration,
    onError: suspend (Throwable) -> Unit = logError,
    action: suspend () -> Unit
): Job = launchAtFixedRate(scope, period, Duration.ZERO, onError, action)
