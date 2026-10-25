package io.elephantchess.webapp.rendering

import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter

fun formatTimestampUtc(epochMillis: Long?, nullPlaceholder: String = "n/a"): String =
    if (epochMillis == null) {
        nullPlaceholder
    } else {
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
            Instant
                .ofEpochMilli(epochMillis)
                .atZone(UTC)
                .toLocalDateTime(),
        ) + " UTC"
    }
