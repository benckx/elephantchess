package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.tables.PasswordRecoveryAttempt.PASSWORD_RECOVERY_ATTEMPT
import io.elephantchess.db.dao.codegen.tables.daos.PasswordRecoveryAttemptDao
import io.elephantchess.db.dao.codegen.tables.pojos.PasswordRecoveryAttempt
import io.elephantchess.db.utils.awaitExecute
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.fixed
import io.elephantchess.db.utils.insertReactive
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock

class PasswordRecoveryAttemptsDaoService(private val dslContext: DSLContext) {

    suspend fun save(pojo: PasswordRecoveryAttempt) {
        dslContext.transactionCoroutine { cfg ->
            PasswordRecoveryAttemptDao(cfg).insertReactive(pojo)
        }
    }

    suspend fun fetchBy(email: String, code: String): PasswordRecoveryAttempt? {
        return dslContext
            .select()
            .from(PASSWORD_RECOVERY_ATTEMPT)
            .where(PASSWORD_RECOVERY_ATTEMPT.EMAIL_PROVIDED.eq(email))
            .and(PASSWORD_RECOVERY_ATTEMPT.RECOVERY_CODE.eq(code))
            .and(PASSWORD_RECOVERY_ATTEMPT.MATCHING_USER_ID.isNotNull)
            .and(PASSWORD_RECOVERY_ATTEMPT.HAS_BEEN_RECOVERED.eq(false))
            .awaitMappedRecords<PasswordRecoveryAttempt>()
            .firstOrNull()
    }

    suspend fun updateRecoveryTime(id: Int) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(PASSWORD_RECOVERY_ATTEMPT)
                .set(PASSWORD_RECOVERY_ATTEMPT.DATE_RECOVERED.fixed(), Clock.System.now())
                .set(PASSWORD_RECOVERY_ATTEMPT.HAS_BEEN_RECOVERED.fixed(), true)
                .where(PASSWORD_RECOVERY_ATTEMPT.ID.fixed().eq(id))
                .awaitExecute()
        }
    }

    suspend fun listLatestAttempts(limit: Int): List<PasswordRecoveryAttempt> {
        return dslContext
            .select()
            .from(PASSWORD_RECOVERY_ATTEMPT)
            .orderBy(PASSWORD_RECOVERY_ATTEMPT.ENTRY_CREATION.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

}
