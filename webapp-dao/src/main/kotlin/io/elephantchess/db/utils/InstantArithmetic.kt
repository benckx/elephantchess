package io.elephantchess.db.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Calendar-aware arithmetic helpers on [kotlin.time.Instant], anchored at UTC.
 *
 * For pure duration math (seconds/minutes/hours/days), prefer the stdlib operators
 * (`instant - 5.days`, `instant + 30.minutes`). The `minusDays`/`minusHours`/`minusSeconds`/`minusMinutes`
 * helpers below mirror the [LocalDateTime] API to keep migration churn low; month / year
 * arithmetic is genuinely calendar-dependent and needs the UTC-anchored variants.
 */

// Pure duration arithmetic (delegates to kotlin.time.Duration).
fun Instant.minusDays(days: Long): Instant = this - days.days
fun Instant.minusHours(hours: Long): Instant = this - hours.hours
fun Instant.minusMinutes(minutes: Long): Instant = this - minutes.minutes
fun Instant.minusSeconds(seconds: Long): Instant = this - seconds.seconds
fun Instant.plusDays(days: Long): Instant = this + days.days
fun Instant.plusHours(hours: Long): Instant = this + hours.hours
fun Instant.plusMinutes(minutes: Long): Instant = this + minutes.minutes
fun Instant.plusSeconds(seconds: Long): Instant = this + seconds.seconds

// Calendar-aware arithmetic; UTC anchor.
fun Instant.minusMonths(months: Long): Instant =
    this.toJavaInstant().atOffset(ZoneOffset.UTC).minusMonths(months).toInstant().toKotlinInstant()

fun Instant.plusMonths(months: Long): Instant =
    this.toJavaInstant().atOffset(ZoneOffset.UTC).plusMonths(months).toInstant().toKotlinInstant()

fun Instant.minusYears(years: Long): Instant =
    this.toJavaInstant().atOffset(ZoneOffset.UTC).minusYears(years).toInstant().toKotlinInstant()

fun Instant.plusYears(years: Long): Instant =
    this.toJavaInstant().atOffset(ZoneOffset.UTC).plusYears(years).toInstant().toKotlinInstant()

// java.time.LocalDateTime / LocalDate ergonomics, anchored at UTC.

/** Convert a [LocalDateTime] (treated as UTC wall-clock) to a [kotlin.time.Instant]. */
fun LocalDateTime.toUtcInstant(): Instant =
    this.toInstant(ZoneOffset.UTC).toKotlinInstant()

/** Convert a [kotlin.time.Instant] to a [LocalDateTime] in UTC. */
fun Instant.toUtcLocalDateTime(): LocalDateTime =
    this.toJavaInstant().atOffset(ZoneOffset.UTC).toLocalDateTime()

fun Instant.toUtcLocalDate(): LocalDate =
    this.toJavaInstant().atOffset(ZoneOffset.UTC).toLocalDate()

/** Build a UTC [Instant] from wall-clock components. */
fun instantOfUtc(
    year: Int,
    month: Int,
    dayOfMonth: Int,
    hour: Int = 0,
    minute: Int = 0,
    second: Int = 0,
): Instant = LocalDateTime.of(year, month, dayOfMonth, hour, minute, second).toUtcInstant()

/** Compatibility shim: `instant.isBefore(other)` mirrors [LocalDateTime.isBefore]. */
fun Instant.isBefore(other: Instant): Boolean = this < other

/** Compatibility shim: `instant.isAfter(other)` mirrors [LocalDateTime.isAfter]. */
fun Instant.isAfter(other: Instant): Boolean = this > other
