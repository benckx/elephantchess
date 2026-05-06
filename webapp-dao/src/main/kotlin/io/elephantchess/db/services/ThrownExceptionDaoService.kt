package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.tables.ThrownException.THROWN_EXCEPTION
import io.elephantchess.db.dao.codegen.tables.daos.ThrownExceptionDao
import io.elephantchess.db.dao.codegen.tables.pojos.ThrownException
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.insertReactive
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

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

}
