package io.elephantchess.engines.utils

import kotlinx.coroutines.delay

object EngineUtils {

    tailrec suspend fun waitForCondition(maxDelay: Long, checkPeriod: Long, condition: () -> Boolean): Boolean {
        if (maxDelay < 0) return false
        if (condition()) return true
        delay(checkPeriod)
        return waitForCondition(maxDelay - checkPeriod, checkPeriod, condition)
    }

}
