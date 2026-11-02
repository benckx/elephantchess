package io.elephantchess.webapp.rendering

import io.elephantchess.utils.stripHtml
import kotlinx.html.meta
import kotlinx.html.stream.createHTML
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.*

private const val SECOND_IN_MILLIS = 1_000L
private const val MINUTE_IN_MILLIS = 60 * SECOND_IN_MILLIS
private const val HOUR_IN_MILLIS = 60 * MINUTE_IN_MILLIS
private const val DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS
private const val MONTH_IN_MILLIS = 30 * DAY_IN_MILLIS
private const val YEAR_IN_MILLIS = 12 * MONTH_IN_MILLIS

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
        elapsed < MINUTE_IN_MILLIS -> formatRelativeValue(elapsed / SECOND_IN_MILLIS, "second")
        elapsed < HOUR_IN_MILLIS -> formatRelativeValue(elapsed / MINUTE_IN_MILLIS, "minute")
        elapsed < DAY_IN_MILLIS -> formatRelativeValue(elapsed / HOUR_IN_MILLIS, "hour")
        elapsed < MONTH_IN_MILLIS -> formatRelativeValue(elapsed / DAY_IN_MILLIS, "day")
        elapsed < YEAR_IN_MILLIS -> formatRelativeValue(elapsed / MONTH_IN_MILLIS, "month")
        else -> formatRelativeValue(elapsed / YEAR_IN_MILLIS, "year")
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
