package io.elephantchess.db.utils

import io.elephantchess.utils.TryEither
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Workaround for insert
 */
fun <R : Record> Table<R>.fixed(): Table<Record> {
    return DSL.table("public.${name.lowercase()}")
}

/**
 * Workaround for inserts and updates
 */
fun <T : Any> Field<T>.fixed(): Field<Any> {
    return DSL.field(DSL.quotedName(name.lowercase()))
}

fun Field<String>.eqIgnoreCaseTrimmed(value: String): Condition =
    DSL.trim(DSL.lower(this)).eq(value.trim().lowercase())

// TODO: can be converted to LocalDate
fun Field<Instant>.date(): Field<java.sql.Date> {
    return DSL.field("date(${this.name})").cast(java.sql.Date::class.java)
}

// TODO: can be converted to LocalDate
fun Field<Instant>.dateQualified(prefix: String): Field<java.sql.Date> {
    return DSL.field("date(${prefix}.${this.name})").cast(java.sql.Date::class.java)
}

fun Field<Instant>.isOlderThan(duration: Duration): Field<Boolean> {
    val limit = Clock.System.now() - duration
    return isBefore(limit)
}

fun Field<Instant>.isBeforeEpochMillis(timestampMillis: Long): Field<Boolean> {
    return isBefore(Instant.fromEpochMilliseconds(timestampMillis))
}

fun Field<Instant>.isWithin(duration: Duration): Field<Boolean> {
    val limit = Clock.System.now() - duration
    return isAfter(limit)
}

fun <T : Any> Field<T>.qualified(prefix: String): Field<Any> {
    return DSL.field("${prefix}.${this.name}")
}

fun Field<Instant>.hourOfDay(): Field<Int> {
    return DSL.field("to_char(${this.name}, 'HH24')")
        .convertFrom { it.toString().toInt() }
        .`as`("hour")
}

fun Field<Instant>.localDate(alias: String? = "day"): Field<LocalDate> {
    val base = DSL
        .field("to_char(${this.name}, 'YYYY-MM-DD')")
        .convertFrom { LocalDate.parse(it.toString()) }

    return if (alias != null) {
        base.`as`(alias)
    } else {
        base
    }
}

fun Field<Instant>.yearMonth(alias: String? = "month"): Field<YearMonth> {
    val base = DSL
        .field("to_char(${this.name}, 'YYYY-MM')")
        .convertFrom { YearMonth.parse(it.toString()) }

    return if (alias != null) {
        base.`as`(alias)
    } else {
        base
    }
}

fun Field<LocalDate>.yearMonthOfDay(): Field<YearMonth> {
    return DSL.field("to_char(${this.name}, 'YYYY-MM')")
        .convertFrom { YearMonth.parse(it.toString()) }
        .`as`("month")
}

fun Field<Instant>.year(): Field<Int> {
    return DSL.field("to_char(${this.name}, 'YYYY')")
        .convertFrom { it.toString().toInt() }
        .`as`("year")
}

fun Field<LocalDate>.yearOfDay(): Field<Int> {
    return DSL.field("to_char(${this.name}, 'YYYY')")
        .convertFrom { it.toString().toInt() }
        .`as`("year")
}

fun Field<Instant>.century(): Field<Int> {
    return DSL.field("to_char(${this.name}, 'CC')")
        .convertFrom { it.toString().toInt() }
        .`as`("century")
}

fun Field<LocalDate>.centuryOfDay(): Field<Int> {
    return DSL.field("to_char(${this.name}, 'CC')")
        .convertFrom { it.toString().toInt() }
        .`as`("century")
}

fun diffInSeconds(f1: Field<Instant>, f2: Field<Instant>): Field<Int> {
    return DSL.extract(f1, DatePart.EPOCH).minus(DSL.extract(f2, DatePart.EPOCH))
}

private fun Field<Instant>.isAfter(timestamp: Instant): Field<Boolean> {
    return greaterThan(timestamp)
}

fun Field<Instant>.isBefore(timestamp: Instant): Field<Boolean> {
    return lessThan(timestamp)
}

suspend fun <T> DSLContext.transactionalContextTry(block: suspend (DSLContext) -> T): TryEither<T> {
    var t: TryEither<T>? = null
    transactionCoroutine { config ->
        t = try {
            TryEither.Valid(block(DSL.using(config)))
        } catch (e: Exception) {
            TryEither.Invalid(e)
        }
    }
    return t!!
}

suspend inline fun <reified T : Any> ResultQuery<out Record>.awaitSingleMappedRecord(): T? {
    return Flux
        .from<Record>(this)
        .collectList()
        .awaitSingle()
        .firstOrNull()
        ?.into<T>(T::class.java)
}

suspend inline fun <reified T : Any> ResultQuery<out Record>.awaitMappedRecords(): List<T> {
    return Flux
        .from<Record>(this)
        .collectList()
        .awaitSingle()
        .map<Record, T> { record -> record.into<T>(T::class.java) }
}

suspend fun <R : Record> ResultQuery<R>.awaitRecords(): List<R> {
    return Flux
        .from(this)
        .collectList()
        .awaitSingle()
}

suspend fun ResultQuery<out Record>.awaitSingleRecord(): Record? {
    return awaitRecords().firstOrNull()
}

suspend inline fun <reified T : Any> ResultQuery<out Record>.awaitSingleOrNull(): T? {
    return awaitRecords()
        .firstOrNull()
        ?.into<T>(T::class.java)
}

@Suppress("UNCHECKED_CAST")
suspend fun <T : Any> ResultQuery<out Record>.awaitSingleValue(): T? {
    return Flux
        .from(this)
        .collectList()
        .awaitSingle()
        .firstOrNull()
        ?.get(0) as? T
}

/**
 * Reactively execute a jOOQ Query (INSERT, UPDATE, DELETE) in an R2DBC context.
 * Returns the number of affected rows.
 */
@Suppress("UNCHECKED_CAST")
suspend fun Query.awaitExecute(): Int {
    return Flux
        .from(this as Publisher<Int>)
        .awaitSingle() ?: 0
}

suspend fun <R : TableRecord<R>, P> DAO<R, P, *>.insertReactive(pojo: P): Int {
    val dslContext = DSL.using(configuration())
    val record = dslContext.newRecord(table)
    record.from(pojo)
    return Flux
        .from(dslContext.insertInto(table).set(record))
        .collectList()
        .awaitSingle()
        .size
}

suspend fun <R : TableRecord<R>, P> DAO<R, P, *>.insertMultipleReactive(pojos: Collection<P>): Int {
    if (pojos.isEmpty()) {
        return 0
    }

    val dslContext = DSL.using(configuration())
    var totalInserted = 0

    // execute each insert reactively
    pojos.forEach { pojo ->
        val record = dslContext.newRecord(table)
        record.from(pojo)
        totalInserted += Flux
            .from(dslContext.insertInto<R>(table).set(record))
            .collectList()
            .awaitSingle()
            .size
    }

    return totalInserted
}
