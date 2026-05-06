package io.elephantchess.servicelayer.metrics

import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.management.ManagementFactory
import java.text.NumberFormat
import java.util.*

class MetricsLogger {

    private val logger = KotlinLogging.logger {}
    private val locale = Locale.Builder().setLanguage("en").setRegion("US").build()
    private val percentFormatter = NumberFormat.getPercentInstance(locale)

    private val rt by lazy {
        Runtime.getRuntime()
    }

    fun logAllMetrics() {
        rt.gc()
        val free = rt.freeMemory()
        val total = rt.totalMemory()
        val max = rt.maxMemory()
        val usage = ((total.toDouble() - free.toDouble()) / total.toDouble())
        val formattedMetrics = listOf(
            "free ${toMegaBytes(free)}",
            "used ${toMegaBytes(total - free)}",
            "total ${toMegaBytes(total)}",
            "max ${toMegaBytes(max)}",
            "usage ${percentFormatter.format(usage)}"
        )
        logger.info { "[heap] ${formattedMetrics.joinToString(", ")}" }
        logger.info { "[available processors] ${rt.availableProcessors()}" }
        logger.info { "[threadCount] ${ManagementFactory.getThreadMXBean().threadCount}" }

        val allThreads = Thread.getAllStackTraces().keys.toList().sortedBy { it.name }

        logger.info {
            allThreads
                .groupBy { it.state }
                .map { (state, threads) -> "[$state] ${threads.size}" }
                .joinToString(", ")
        }
        logger.info {
            allThreads
                .groupBy { it.threadGroup?.name ?: "none" }
                .map { (group, threads) -> "[$group] ${threads.size}" }
                .joinToString(", ")
        }
        logger.info {
            allThreads
                .groupBy { it.isAlive }
                .map { (isAlive, threads) -> "[${if (isAlive) "alive" else "zombie"}] ${threads.size}" }
                .joinToString(", ")
        }
        logger.info {
            allThreads
                .groupBy { it.isVirtual }
                .map { (isDaemon, threads) -> "[${if (isDaemon) "virtual" else "non-virtual"}] ${threads.size}" }
                .joinToString(", ")
        }

        logger.info { allThreads.joinToString(", ") { it.name } }
    }

    private companion object {

        const val MEGA_BYTES = 1024L * 1024L

        fun toMegaBytes(bytes: Long): String {
            return "${(bytes / MEGA_BYTES).toInt()} MB"
        }

    }

}
