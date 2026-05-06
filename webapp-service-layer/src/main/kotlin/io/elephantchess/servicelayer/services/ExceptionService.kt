package io.elephantchess.servicelayer.services

import io.elephantchess.db.dao.codegen.tables.pojos.ThrownException
import io.elephantchess.db.services.ThrownExceptionDaoService
import io.elephantchess.db.utils.generateId
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock

class ExceptionService(
    private val daoService: ThrownExceptionDaoService,
    private val logger: KLogger,
) {

    private val exceptionServiceScope by lazy { CoroutineScope(Dispatchers.Default) }

    fun saveException(
        throwable: Throwable,
        httpCode: Int,
    ) {
        exceptionServiceScope.launch {
            try {
                val record = ThrownException()
                record.id = generateId()
                record.exceptionTime = Clock.System.now()
                record.httpCode = httpCode
                record.exceptionClass = throwable::class.qualifiedName ?: throwable::class.simpleName ?: "Unknown"
                record.exceptionMessage = throwable.message ?: throwable.toString()
                daoService.save(record)
            } catch (e: Exception) {
                logger.error(e) { "Error saving exception to database" }
            }
        }
    }

}
