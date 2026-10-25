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
