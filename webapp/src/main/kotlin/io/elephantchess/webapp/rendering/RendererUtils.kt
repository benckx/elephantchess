package io.elephantchess.webapp.rendering

import io.elephantchess.utils.stripHtml
import kotlinx.html.meta
import kotlinx.html.stream.createHTML
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.*

fun formatTimestampUtc(epochMillis: Long?, nullPlaceholder: String = "n/a"): String =
    if (epochMillis == null) {
        nullPlaceholder
    } else {
        val dateTime = Instant.ofEpochMilli(epochMillis).atZone(UTC).toLocalDateTime()
        val currentYear = LocalDate.now(UTC).year
        val hasCurrentYear = dateTime.year == currentYear
        val pattern = if (hasCurrentYear) "EEE. d MMM. HH:mm" else "EEE. d MMM. yyyy HH:mm"

        dateTime.format(DateTimeFormatter.ofPattern(pattern).withLocale(Locale.ENGLISH))
    }

fun formatTimestampToRelativeTime(epochMillis: Long?, nullPlaceholder: String = "n/a"): String {
    if (epochMillis == null) {
        return nullPlaceholder
    }

    val elapsed = Instant.now().toEpochMilli() - epochMillis
    if (elapsed <= 0L) {
        return "just now"
    }

    return when {
        elapsed < 60_000L -> formatRelativeValue(elapsed / 1_000L, "second")
        elapsed < 3_600_000L -> formatRelativeValue(elapsed / 60_000L, "minute")
        elapsed < 86_400_000L -> formatRelativeValue(elapsed / 3_600_000L, "hour")
        elapsed < 2_592_000_000L -> formatRelativeValue(elapsed / 86_400_000L, "day")
        elapsed < 31_104_000_000L -> formatRelativeValue(elapsed / 2_592_000_000L, "month")
        else -> formatRelativeValue(elapsed / 31_104_000_000L, "year")
    }
}

private fun formatRelativeValue(value: Long, unit: String): String {
    val suffix = if (value == 1L) unit else "${unit}s"
    return "$value $suffix ago"
}

/**
 * HTML-escapes a string so it can be safely inlined as text content.
 */
fun escapeHtml(s: String): String =
    s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

/**
 * HTML-escapes a string so it can be safely used as an attribute value.
 */
fun escapeHtmlAttr(s: String): String =
    escapeHtml(s).replace("\"", "&quot;")

fun String.toParagraphs(): List<String> =
    this
        .split("\n\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

fun descriptionMeta(description: String): String =
    createHTML().meta {
        name = "description"
        content = stripHtml(
            description
                .replace("\n", " ")
                .replace("\r", ""),
        )
    }
