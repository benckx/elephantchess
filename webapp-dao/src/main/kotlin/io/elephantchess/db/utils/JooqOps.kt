package io.elephantchess.db.utils

import io.elephantchess.utils.TryEither
import kotlinx.coroutines.reactive.awaitSingle
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

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

fun Field<Instant>.isBeforeEpochMillis(timestampMillis: Long): Condition {
    return isBefore(Instant.fromEpochMilliseconds(timestampMillis))
}

fun Field<Instant>.isWithin(duration: Duration): Condition {
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

/**
 * The UTC calendar day of an instant, bucketed via `to_char(..., 'YYYY-MM-DD')` and cast to a genuine
 * `date` SQL expression (so it can be grouped/selected and also inserted into a `date` column).
 * When [alias] is non-null the field is aliased (defaulting to `day`).
 */
fun Field<Instant>.localDate(alias: String? = "day"): Field<LocalDate> {
    val base = DSL.field("cast(to_char({0}, 'YYYY-MM-DD') as date)", SQLDataType.LOCALDATE, this)
    return if (alias != null) base.`as`(alias) else base
}

/**
 * The current transaction timestamp rendered for [target], reusing that column's data type so the
 * `timestamptz`/[Instant] forced-type converter is applied when assigning it on upsert conflicts.
 */
fun <T> currentTimestamp(target: Field<T>): Field<T> =
    DSL.field("current_timestamp", target.dataType)

/**
 * On an `ON CONFLICT ... DO UPDATE` upsert, accumulate [field] by adding the value that would have been
 * inserted (`excluded.field`) to the existing row's value, i.e. `set(field, field + excluded(field))`.
 * Chainable across several columns.
 */
fun <R : Record, T : Number> InsertOnDuplicateSetStep<R>.accumulate(
    field: Field<T>,
): InsertOnDuplicateSetMoreStep<R> =
    set(field, field.plus(DSL.excluded(field)))

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

fun Field<Instant>.isAfter(timestamp: Instant): Condition {
    return greaterThan(timestamp)
}

fun Field<Instant>.isBefore(timestamp: Instant): Condition {
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
        .from(this)
        .collectList()
        .awaitSingle()
        .firstOrNull()
        ?.into<T>(T::class.java)
}

suspend inline fun <reified T : Any> ResultQuery<out Record>.awaitMappedRecords(): List<T> {
    return Flux
        .from(this)
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
