package io.elephantchess.webapp.server

import io.elephantchess.config.AppConfig
import io.elephantchess.servicelayer.utils.ops.koin
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}
private val appConfig by koin<AppConfig>()

/**
 * Installs a per-IP rate limiter in front of the public reference-database HTML pages to throttle
 * scrapers. Runs early in the pipeline (before routing) and short-circuits offending requests with a
 * `429 Too Many Requests` and a `Retry-After` header.
 *
 * Disabled requests (non-monitored paths, non-GET, allow-listed crawlers, or under the limit) fall
 * through untouched. See [DatabaseRateLimiter] for the matching and allow-list logic.
 */
fun Application.databaseRateLimitModule() {
    if (!appConfig.isDatabaseRateLimitEnabled) {
        logger.warn { "database rate limiting is disabled" }
        return
    }

    val maxRequests = appConfig.databaseRateLimitMaxRequests
    val windowSeconds = appConfig.databaseRateLimitWindowSeconds
    val limiter = DatabaseRateLimiter(maxRequests = maxRequests, window = windowSeconds.seconds)

    logger.info { "database rate limiting enabled: $maxRequests requests / ${windowSeconds}s per IP" }

    intercept(ApplicationCallPipeline.Plugins) {
        val rateLimited =
            limiter.isRateLimited(
                httpMethod = call.request.httpMethod.value,
                path = call.request.path(),
                userAgent = call.request.headers[HttpHeaders.UserAgent],
                remoteAddress = call.request.origin.remoteAddress,
                forwardedFor = call.request.headers["X-Forwarded-For"],
            )

        if (rateLimited) {
            logger.debug {
                "rate limited ${call.request.httpMethod.value} ${call.request.path()} " +
                        "| User-Agent: ${call.request.headers[HttpHeaders.UserAgent]?.take(100)}"
            }
            call.response.headers.append(HttpHeaders.RetryAfter, limiter.retryAfterSeconds.toString())
            call.respondText(
                text = "Too many requests. Please slow down.",
                status = HttpStatusCode.TooManyRequests,
            )
            finish()
        }
    }
}
