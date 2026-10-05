package io.elephantchess.webapp.server

import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseRateLimiterTest {

    private val chromeScraperUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36"

    private fun limiter(max: Int = 3, windowSeconds: Int = 60) =
        DatabaseRateLimiter(maxRequests = max, window = windowSeconds.seconds)

    private fun DatabaseRateLimiter.hit(
        path: String = "/database/player/Wang_Tianyi",
        ua: String? = chromeScraperUa,
        ip: String = "203.0.113.7",
        method: String = "GET",
        forwardedFor: String? = null,
    ) = isRateLimited(
        httpMethod = method,
        path = path,
        userAgent = ua,
        remoteAddress = ip,
        forwardedFor = forwardedFor,
    )

    @Test
    fun `allows requests up to the limit then blocks`() {
        val limiter = limiter(max = 3)
        assertFalse(limiter.hit()) // 1
        assertFalse(limiter.hit()) // 2
        assertFalse(limiter.hit()) // 3
        assertTrue(limiter.hit())  // 4 -> over the limit
        assertTrue(limiter.hit())  // 5
    }

    @Test
    fun `limits are tracked independently per IP`() {
        val limiter = limiter(max = 2)
        assertFalse(limiter.hit(ip = "1.1.1.1"))
        assertFalse(limiter.hit(ip = "1.1.1.1"))
        assertTrue(limiter.hit(ip = "1.1.1.1"))
        // a different IP still has its full allowance
        assertFalse(limiter.hit(ip = "2.2.2.2"))
        assertFalse(limiter.hit(ip = "2.2.2.2"))
        assertTrue(limiter.hit(ip = "2.2.2.2"))
    }

    @Test
    fun `requests sharing a forwarded-for client IP are counted together`() {
        val limiter = limiter(max = 2)
        // same client IP, different proxy socket peers -> same bucket
        assertFalse(limiter.hit(ip = "10.0.0.1", forwardedFor = "198.51.100.5, 10.0.0.1"))
        assertFalse(limiter.hit(ip = "10.0.0.2", forwardedFor = "198.51.100.5"))
        assertTrue(limiter.hit(ip = "10.0.0.3", forwardedFor = "198.51.100.5, 10.0.0.9"))
    }

    @Test
    fun `never limits well-known search crawlers`() {
        val limiter = limiter(max = 1)
        val googlebot = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
        repeat(50) { assertFalse(limiter.hit(ua = googlebot)) }

        val bingbot = "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)"
        repeat(50) { assertFalse(limiter.hit(ua = bingbot)) }
    }

    @Test
    fun `only monitored database paths are limited`() {
        val limiter = limiter(max = 1)
        // not monitored -> never limited regardless of volume
        repeat(10) { assertFalse(limiter.hit(path = "/game?id=abc")) }
        repeat(10) { assertFalse(limiter.hit(path = "/")) }
    }

    @Test
    fun `browse event is monitored`() {
        val limiter = limiter(max = 1)
        assertFalse(limiter.hit(path = "/browse/event?id=abc"))
        assertTrue(limiter.hit(path = "/browse/event?id=abc"))
    }

    @Test
    fun `non-GET requests are not limited`() {
        val limiter = limiter(max = 1)
        repeat(10) { assertFalse(limiter.hit(method = "POST")) }
    }

    @Test
    fun `monitored path detection ignores query string and avoids false prefixes`() {
        // monitored: limited on the 2nd hit (max = 1)
        for (path in listOf("/database", "/database/player/Wang_Tianyi", "/database/game?id=abc", "/browse/event?id=abc")) {
            val limiter = limiter(max = 1)
            assertFalse(limiter.hit(path = path), "first hit to $path should pass")
            assertTrue(limiter.hit(path = path), "second hit to $path should be limited")
        }

        // not monitored: never limited, even with a similar prefix
        for (path in listOf("/databases", "/game", "/")) {
            val limiter = limiter(max = 1)
            repeat(5) { assertFalse(limiter.hit(path = path), "$path must never be limited") }
        }
    }

    @Test
    fun `crawler allow-list is case-insensitive and loaded from the resource file`() {
        // tokens come from /config/crawler_user_agents.txt
        val crawlerUserAgents = listOf(
            "something GOOGLEBOT something",
            "Applebot/0.1",
            "Mozilla/5.0 (compatible; GPTBot/1.0; +https://openai.com/gptbot)",
        )
        for (ua in crawlerUserAgents) {
            val limiter = limiter(max = 1)
            repeat(10) { assertFalse(limiter.hit(ua = ua), "crawler UA must never be limited: $ua") }
        }

        // a regular browser UA is still subject to the limit
        val limiter = limiter(max = 1)
        assertFalse(limiter.hit(ua = chromeScraperUa))
        assertTrue(limiter.hit(ua = chromeScraperUa))
    }

    @Test
    fun `distinct forwarded-for client IPs get independent buckets`() {
        val limiter = limiter(max = 1)
        assertFalse(limiter.hit(ip = "10.0.0.1", forwardedFor = "198.51.100.5, 10.0.0.1"))
        assertTrue(limiter.hit(ip = "10.0.0.1", forwardedFor = "198.51.100.5"))
        // a different client IP (same proxy socket peer) keeps its own allowance
        assertFalse(limiter.hit(ip = "10.0.0.1", forwardedFor = "203.0.113.9"))
    }
}
