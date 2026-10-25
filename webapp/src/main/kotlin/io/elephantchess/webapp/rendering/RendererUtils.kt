package io.elephantchess.webapp.rendering

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.util.Locale

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
