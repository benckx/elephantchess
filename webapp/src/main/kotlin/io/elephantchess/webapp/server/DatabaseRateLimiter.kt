package io.elephantchess.webapp.server

import io.elephantchess.servicelayer.utils.extractAddress
import io.elephantchess.utils.ResourceUtils
import io.github.reactivecircus.cache4k.Cache
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/**
 * Per-IP rate limiter for the public reference-database HTML pages (`/database/...`, `/browse/event`).
 *
 * Context: these pages are repeatedly enumerated by scrapers (player and game crawling) using cookie-less
 * headless browsers that advertise a regular browser user-agent (e.g. "Chrome"). A per-IP fixed-window
 * limit cheaply throttles that sustained, concentrated traffic while leaving genuine visitors (who only
 * ever view a handful of database pages) unaffected.
 *
 * Well-known search / AI crawler user-agents are always exempt so indexing and SEO are never impacted.
 *
 * The counters are kept in-memory (per pod). With multiple pods behind the load balancer the effective
 * limit is multiplied by the number of pods, which is acceptable for this best-effort protection.
 */
class DatabaseRateLimiter(private val maxRequests: Int, window: Duration) {

    private val counters: Cache<String, AtomicInteger> =
        Cache
            .Builder<String, AtomicInteger>()
            .expireAfterWrite(window)
            .build()

    /**
     * Returns true when a request to [path] with [httpMethod] from [remoteAddress]/[forwardedFor] using
     * [userAgent] must be rejected because the IP exceeded its allowance within the current window.
     *
     * Non-monitored paths, non-GET methods and allow-listed crawler user-agents are never rate limited.
     */
    fun isRateLimited(
        httpMethod: String,
        path: String,
        userAgent: String?,
        remoteAddress: String,
        forwardedFor: String?,
    ): Boolean {
        if (!httpMethod.equals("GET", ignoreCase = true)) return false
        if (!isMonitoredPath(path)) return false
        if (isAllowlistedCrawler(userAgent)) return false

        val key = clientKey(remoteAddress, forwardedFor)
        val counter = counters.get(key) ?: AtomicInteger(0).also { counters.put(key, it) }
        return counter.incrementAndGet() > maxRequests
    }

    private companion object {

        /** Path prefixes of the public reference-database pages targeted by scrapers. */
        val MONITORED_PREFIXES = listOf("/database", "/browse/event")

        /**
         * Lower-cased substrings identifying well-known search engine and AI crawlers that must never be
         * rate limited (protects indexing / SEO). Matched case-insensitively against the User-Agent.
         *
         * Loaded from the bundled classpath resource so the list can be edited without touching code; the
         * resource travels inside the jar, so it also resolves when the app runs Dockerized. Blank lines
         * and '#' comments are ignored.
         */
        val CRAWLER_USER_AGENT_TOKENS: List<String> =
            ResourceUtils
                .resourceAsLines("/config/crawler-user-agents.txt")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.lowercase() }

        fun isMonitoredPath(path: String): Boolean {
            val normalized = path.substringBefore('?')
            return MONITORED_PREFIXES.any { prefix ->
                normalized == prefix || normalized.startsWith("$prefix/")
            }
        }

        fun isAllowlistedCrawler(userAgent: String?): Boolean {
            if (userAgent.isNullOrBlank()) return false
            val lower = userAgent.lowercase()
            return CRAWLER_USER_AGENT_TOKENS.any { token -> lower.contains(token) }
        }

        /**
         * Resolves the stable rate-limit key for a request: the first (client) IP of the forwarded-for
         * chain when present, otherwise the socket peer address.
         */
        fun clientKey(remoteAddress: String, forwardedFor: String?): String {
            val headers =
                if (forwardedFor != null) mapOf("x-forwarded-for" to listOf(forwardedFor)) else emptyMap()
            val address = extractAddress(remoteAddress, headers) ?: remoteAddress
            return address.substringBefore(',').trim()
        }
    }

}
