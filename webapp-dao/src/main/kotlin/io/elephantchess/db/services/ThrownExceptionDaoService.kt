package io.elephantchess.db.services

import io.elephantchess.utils.di.KoinSingleton

import io.elephantchess.db.dao.codegen.tables.ThrownException.THROWN_EXCEPTION
import io.elephantchess.db.dao.codegen.tables.daos.ThrownExceptionDao
import io.elephantchess.db.dao.codegen.tables.pojos.ThrownException
import io.elephantchess.db.model.ThrownExceptionMonthlyCount
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.awaitRecords
import io.elephantchess.db.utils.insertReactive
import io.elephantchess.db.utils.minusMonths
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import java.time.YearMonth
import kotlin.time.Clock

@KoinSingleton
class ThrownExceptionDaoService(private val dslContext: DSLContext) {

    suspend fun save(record: ThrownException) {
        dslContext.transactionCoroutine { cfg ->
            ThrownExceptionDao(cfg).insertReactive(record)
        }
    }

    suspend fun listLatestExceptions(
        limit: Int,
        httpCodeFilter: String?
    ): List<ThrownException> {
        val conditions = mutableListOf<org.jooq.Condition>()

        when (httpCodeFilter) {
            "4xx", "400" -> conditions += THROWN_EXCEPTION.HTTP_CODE.between(400, 499)
            "5xx", "500" -> conditions += THROWN_EXCEPTION.HTTP_CODE.between(500, 599)
        }

        return dslContext
            .selectFrom(THROWN_EXCEPTION)
            .where(conditions)
            .orderBy(THROWN_EXCEPTION.EXCEPTION_TIME.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

    /**
     * Counts thrown exceptions grouped by month, split into HTTP 4xx (client) and 5xx (server) errors.
     * Exceptions without an HTTP code or outside the 400-599 range are not counted in either bucket.
     */
    suspend fun fetchExceptionCountsByMonth(months: Int = 12): List<ThrownExceptionMonthlyCount> {
        val monthsAgo = Clock.System.now().minusMonths(months.toLong())

        val yearField = DSL.extract(THROWN_EXCEPTION.EXCEPTION_TIME, org.jooq.DatePart.YEAR)
        val monthField = DSL.extract(THROWN_EXCEPTION.EXCEPTION_TIME, org.jooq.DatePart.MONTH)
        val clientErrorsField = DSL
            .count()
            .filterWhere(THROWN_EXCEPTION.HTTP_CODE.between(400, 499))
            .`as`("client_errors")
        val serverErrorsField = DSL
            .count()
            .filterWhere(THROWN_EXCEPTION.HTTP_CODE.between(500, 599))
            .`as`("server_errors")

        return dslContext
            .select(
                yearField.`as`("year"),
                monthField.`as`("month"),
                clientErrorsField,
                serverErrorsField
            )
            .from(THROWN_EXCEPTION)
            .where(THROWN_EXCEPTION.EXCEPTION_TIME.ge(monthsAgo))
            .groupBy(yearField, monthField)
            .orderBy(yearField, monthField)
            .awaitRecords()
            .map { record ->
                ThrownExceptionMonthlyCount(
                    month = YearMonth.of(
                        record.get("year", Int::class.java),
                        record.get("month", Int::class.java)
                    ),
                    clientErrors = record.get("client_errors", Int::class.java),
                    serverErrors = record.get("server_errors", Int::class.java)
                )
            }
    }

}
