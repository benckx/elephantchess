package io.elephantchess.webapp.server

import io.elephantchess.config.AppConfig
import io.elephantchess.engines.EnginePool
import io.elephantchess.engines.process.FairyStockfishEngineId
import io.elephantchess.engines.process.PikafishEngineId
import io.elephantchess.servicelayer.services.MailService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.xiangqi.Board.Companion.DEFAULT_START_FEN
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger("HealthCheck")
private val enginePool by koin<EnginePool>()
private val appConfig by koin<AppConfig>()
private val mailService by koin<MailService>()

private const val HEALTH_CHECK_DEPTH = 6
private const val HEALTH_CHECK_TIMEOUT = 15_000L

private val MAIL_COOLDOWN = 10.minutes
private val LOG_COOLDOWN = 5.minutes

private data class HealthCheckStats(
    val lastMailSentAt: Instant = Instant.EPOCH,
    val lastStatsLogAt: Instant = Instant.EPOCH,
    val count: Int = 0,
    val minMs: Long = Long.MAX_VALUE,
    val maxMs: Long = Long.MIN_VALUE,
    val totalMs: Long = 0,
)

@Volatile
private var stats = HealthCheckStats()

fun Application.healthCheckModule() {
    routing {
        get("/api/is-ready") {
            // so should swap to this one
            call.respond(OK, "OK")
        }
        get("/api/health") {
            // let's keep this one for backward compatibility, but it should be replaced by /api/ready
            call.respond(OK, "OK")
        }
        get("/api/engine-pool-health") {
            if (!appConfig.isEnginePoolEnabled) {
                call.respond(OK, "engine pool disabled")
                return@get
            }

            // sometimes the engine process seems to get stuck and stop respond,
            // so the health check will try to run a quick search on both engines
            // and see if they respond in a reasonable time.
            val errors = mutableListOf<String>()
            val elapsedTime = measureTimeMillis {
                listOf(PikafishEngineId, FairyStockfishEngineId).forEach { engineId ->
                    try {
                        val result = enginePool.queryForDepth(
                            fen = DEFAULT_START_FEN,
                            engineId = engineId,
                            depth = HEALTH_CHECK_DEPTH,
                            timeout = HEALTH_CHECK_TIMEOUT,
                        )

                        logger.debug {
                            val resultStr = (result?.infoLines ?: emptyList())
                                .filter { it.depth == HEALTH_CHECK_DEPTH }
                                .joinToString(", ")

                            "health check for ${engineId.displayName}: $resultStr"
                        }

                        if (result == null) {
                            errors += "${engineId.displayName}: no result from engine"
                        }
                    } catch (e: Exception) {
                        errors += "${engineId.displayName}: ${e.message}"
                    }
                }
            }

            if (errors.isEmpty()) {
                recordSuccessStats(elapsedTime)
                call.respond(OK, "OK")
            } else {
                logger.error { "engine pool health check failed: $errors" }
                notifyAdmin(errors)
                call.respond(InternalServerError, errors.joinToString("; "))
            }
        }
    }
}

private fun recordSuccessStats(elapsedTime: Long) {
    stats = stats.copy(
        count = stats.count + 1,
        minMs = minOf(stats.minMs, elapsedTime),
        maxMs = maxOf(stats.maxMs, elapsedTime),
        totalMs = stats.totalMs + elapsedTime,
    )

    val now = Instant.now()
    if (now.toEpochMilli() - stats.lastStatsLogAt.toEpochMilli() >= LOG_COOLDOWN.inWholeMilliseconds) {
        val s = stats
        val avg = if (s.count > 0) s.totalMs / s.count else 0
        stats = stats.copy(
            lastStatsLogAt = now,
            count = 0,
            minMs = Long.MAX_VALUE,
            maxMs = Long.MIN_VALUE,
            totalMs = 0,
        )
        logger.info { "engine pool health check stats: count=${s.count}, min=${s.minMs}ms, max=${s.maxMs}ms, avg=${avg}ms" }
    }
}

private fun notifyAdmin(errors: List<String>) {
    val now = Instant.now()
    if (now.toEpochMilli() - stats.lastMailSentAt.toEpochMilli() >= MAIL_COOLDOWN.inWholeMilliseconds) {
        stats = stats.copy(lastMailSentAt = now)
        try {
            mailService.sendEnginePoolHealthCheckFailed(errors)
        } catch (e: Exception) {
            logger.error(e) { "failed to send engine pool health check notification" }
        }
    }
}
