package io.elephantchess.servicelayer.utils.ops

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.time.Duration

/**
 * Launches a coroutine that runs a suspend action at a fixed rate.
 * Returns a Job that can be cancelled to stop the periodic execution.
 *
 * @param scope The coroutine scope to launch in
 * @param period The time between consecutive executions
 * @param initialDelay The time to wait before the first execution (default: 0)
 * @param action The suspend function to execute periodically
 */
fun launchAtFixedRate(
    scope: CoroutineScope,
    period: Duration,
    initialDelay: Duration = Duration.ZERO,
    action: suspend () -> Unit
): Job {
    val logger = KotlinLogging.logger {}

    return scope.launch {
        delay(initialDelay)

        while (isActive) {
            try {
                action()
            } catch (e: CancellationException) {
                throw e // Re-throw to properly cancel the coroutine
            } catch (e: Exception) {
                logger.error(e) { "error in periodic coroutine task" }
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
 * @param action The suspend function to execute periodically
 */
fun launchAtFixedRateStartImmediately(
    scope: CoroutineScope,
    period: Duration,
    action: suspend () -> Unit
): Job = launchAtFixedRate(scope, period, Duration.ZERO, action)
