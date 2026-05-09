package io.elephantchess.webapp.rendering

import io.elephantchess.servicelayer.dto.kofi.LatestSupporter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

class TagResolversTest {

    // March 15, 2020 00:00:00 UTC -- using a year clearly in the past so the
    // formatted date is deterministic ("MMM. d yyyy").
    private val pastTimestamp = 1584230400000L
    private val pastDateStr = "Mar. 15 2020"

    private suspend fun render(supporter: LatestSupporter?): String {
        val resolver = latestSupporterTagResolver(supporter)
        assertEquals("latest_supporter_banner_content", resolver.tagName)
        val out = resolver.resolveContent().joinToString("")
        // Log the dynamic "Their ..." sentence so the rendered message can be proof-read.
        val sentence = extractTheirSentence(out)
        if (sentence != null) {
            logger.info { "rendered sentence: $sentence" }
        }
        return out
    }

    /** Extracts the dynamic "Their ..." sentence (without surrounding HTML tags) for proof-reading. */
    private fun extractTheirSentence(html: String): String? {
        val start = html.indexOf("Their ")
        if (start < 0) return null
        // Sentence ends right before " Many thanks to them!"
        val end = html.indexOf(" Many thanks to them!", start)
        val raw = if (end > start) html.substring(start, end) else html.substring(start)
        // Strip HTML tags so the logged output is easy to read.
        return raw.replace(Regex("<[^>]+>"), "")
    }

    private fun lastSupporter(
        amount: Double,
        currency: String,
        recurring: Boolean = false,
        userId: String? = "user-id",
        username: String = "alice",
        timestamp: Long = pastTimestamp,
    ) = LatestSupporter(
        userId = userId,
        username = username,
        timestamp = timestamp,
        amount = amount,
        currency = currency,
        recurring = recurring,
    )

    @Test
    fun `null supporter returns empty string`() = runTest {
        assertEquals("", render(null))
    }

    @Test
    fun `eur one-time tip enough for several days`() = runTest {
        val out = render(lastSupporter(amount = 12.0, currency = "EUR"))
        assertEquals(
            """<p>Our latest donation was offered to us by <b><a href="/@/alice">alice</a></b>. """ +
                    """Their generous 12€ tip on $pastDateStr allowed us to finance the platform for """ +
                    """<b>3 entire days!</b> Many thanks to them!</p>""" +
                    """<p>Do you want to see <b>your name</b> featured as our latest supporter? """ +
                    """<b><a href="https://ko-fi.com/elephantchess" target="_blank">You, too, can help us</a></b> """ +
                    """and with just 4€ finance the platform for an entire day!</p>""",
            out
        )
    }

    @Test
    fun `eur one-time tip exactly one day uses singular phrasing`() = runTest {
        val out = render(lastSupporter(amount = 4.0, currency = "EUR"))
        assertTrue(
            out.contains("Their generous 4€ tip on $pastDateStr allowed us to finance the platform for <b>an entire day!</b>"),
            "Unexpected output: $out"
        )
    }

    @Test
    fun `eur one-time tip below one day shows few hours`() = runTest {
        val out = render(lastSupporter(amount = 2.0, currency = "EUR"))
        assertTrue(
            out.contains("Their 2€ tip on $pastDateStr allowed us to finance the platform for a few hours!"),
            "Unexpected output: $out"
        )
    }

    @Test
    fun `usd recurring pledge with several days uses every month`() = runTest {
        val out = render(lastSupporter(amount = 20.0, currency = "USD", recurring = true))
        assertTrue(
            out.contains("Their generous 20\$ pledge on $pastDateStr allows us to finance the platform for <b>5 entire days every month!</b>"),
            "Unexpected output: $out"
        )
    }

    @Test
    fun `gbp recurring pledge below one day uses every month suffix`() = runTest {
        val out = render(lastSupporter(amount = 2.0, currency = "GBP", recurring = true))
        assertTrue(
            out.contains("Their 2£ pledge on $pastDateStr allows us to finance the platform for a few hours every month!"),
            "Unexpected output: $out"
        )
    }

    @Test
    fun `unsupported currency falls back to generic message`() = runTest {
        val out = render(lastSupporter(amount = 1500.0, currency = "JPY"))
        // Look at only the dynamic first paragraph (the second one has static promo text).
        val firstParagraph = out.substringBefore("</p>") + "</p>"
        assertTrue(
            firstParagraph.contains("Their 1500JPY tip on $pastDateStr allowed us to finance the platform for a little bit!"),
            "Unexpected first paragraph: $firstParagraph"
        )
        assertTrue(!firstParagraph.contains("entire day"), "Should not include day estimate: $firstParagraph")
        assertTrue(!firstParagraph.contains("a few hours"), "Should not include hours estimate: $firstParagraph")
    }

    @Test
    fun `unsupported currency recurring uses every month and present tense`() = runTest {
        val out = render(lastSupporter(amount = 500.0, currency = "INR", recurring = true))
        assertTrue(
            out.contains("Their 500INR pledge on $pastDateStr allows us to finance the platform every month for a little bit!"),
            "Unexpected output: $out"
        )
    }

    @Test
    fun `fractional amount keeps decimals`() = runTest {
        val out = render(lastSupporter(amount = 5.5, currency = "EUR"))
        assertTrue(out.contains("5.5€"), "Expected decimal amount in: $out")
    }

    @Test
    fun `whole amount drops decimals`() = runTest {
        val out = render(lastSupporter(amount = 5.0, currency = "EUR"))
        assertTrue(out.contains("5€"), "Expected integer amount in: $out")
        assertTrue(!out.contains("5.0€"), "Should not contain decimal: $out")
    }

    @Test
    fun `anonymous supporter without userId is not linked`() = runTest {
        val out = render(lastSupporter(amount = 8.0, currency = "EUR", userId = null, username = "bob"))
        assertTrue(out.contains("offered to us by <b>bob</b>"), "Expected unlinked username in: $out")
        assertTrue(!out.contains("/@/bob"), "Should not contain profile link: $out")
    }

    @Test
    fun `currency matching is case insensitive`() = runTest {
        val out = render(lastSupporter(amount = 8.0, currency = "eur"))
        assertTrue(
            out.contains("entire days") || out.contains("entire day"),
            "Expected days-based message for lower-case eur: $out"
        )
    }

    @Test
    fun `current-year date omits year`() = runTest {
        val now = LocalDate.now(UTC)
        val jan5 = LocalDate.of(now.year, 1, 5)
        val ts = jan5.atStartOfDay(UTC).toInstant().toEpochMilli()
        val out = render(lastSupporter(amount = 4.0, currency = "EUR", timestamp = ts))
        assertTrue(out.contains("on Jan. 5 "), "Expected current-year date without year in: $out")
        assertTrue(!out.contains("Jan. 5 ${now.year}"), "Should not include year for current-year date: $out")
    }
}
