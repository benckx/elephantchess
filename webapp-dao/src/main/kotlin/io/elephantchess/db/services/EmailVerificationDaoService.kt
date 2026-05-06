package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.EmailVerificationBounceDao
import io.elephantchess.db.dao.codegen.tables.daos.EmailVerificationDao
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerification
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerificationBounce
import io.elephantchess.db.utils.*
import io.github.oshai.kotlinlogging.KLogger
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Duration

class EmailVerificationDaoService(
    private val dslContext: DSLContext,
    private val logger: KLogger
) {

    suspend fun save(record: EmailVerification) {
        dslContext.transactionCoroutine { cfg ->
            EmailVerificationDao(cfg).insertReactive(record)
        }
    }

    suspend fun insertOrUpdateBounced(records: List<EmailVerificationBounce>) {
        dslContext.transactionCoroutine { cfg ->
            records.forEach { record ->
                val found = DSL
                    .using(cfg)
                    .selectFrom(EMAIL_VERIFICATION_BOUNCE)
                    .where(EMAIL_VERIFICATION_BOUNCE.EMAIL.eq(record.email))
                    .awaitMappedRecords<EmailVerificationBounce>().firstOrNull()

                if (found == null) {
                    EmailVerificationBounceDao(cfg).insertReactive(record)
                    logger.info { "bounced email inserted $record" }
                } else if (record != found) {
                    EmailVerificationBounceDao(cfg).insertReactive(record)
                    logger.info { "bounced email updated $record" }
                } else {
                    logger.info { "bounced email already in the database $record" }
                }
            }
        }
    }

    suspend fun listBouncedEmails(): List<String> {
        return dslContext
            .select(EMAIL_VERIFICATION_BOUNCE.EMAIL)
            .from(EMAIL_VERIFICATION_BOUNCE)
            .orderBy(EMAIL_VERIFICATION_BOUNCE.BOUNCED_TIME)
            .awaitMappedRecords()
    }

    /**
     * Case-insensitive and trim-insensitive
     */
    suspend fun hasEmailBounced(email: String): Boolean {
        return dslContext
            .select(EMAIL_VERIFICATION_BOUNCE.EMAIL)
            .from(EMAIL_VERIFICATION_BOUNCE)
            .where(EMAIL_VERIFICATION_BOUNCE.EMAIL.eqIgnoreCaseTrimmed(email))
            .awaitSingleValue<String>() != null
    }

    suspend fun listEmailsToVerify(limit: Duration): List<String> {
        val verifiedRecently = dslContext
            .selectDistinct(EMAIL_VERIFICATION.EMAIL)
            .from(EMAIL_VERIFICATION)
            .where(
                EMAIL_VERIFICATION.VERIFICATION_TIME.isWithin(limit)
            )

        val haveAlreadyFailedVerification = dslContext
            .selectDistinct(EMAIL_VERIFICATION.EMAIL)
            .from(EMAIL_VERIFICATION)
            .where(
                EMAIL_VERIFICATION.RESULT.notIn("ok", "ok_for_all")
            )

        val bouncedEmails = dslContext
            .select(EMAIL_VERIFICATION_BOUNCE.EMAIL)
            .from(EMAIL_VERIFICATION_BOUNCE)

        return dslContext
            .select(USER.EMAIL)
            .from(USER)
            .where(USER.EMAIL.isNotNull)
            .and(
                // exclude the ones verified not long ago
                USER.EMAIL.notIn(verifiedRecently)
            )
            .and(
                // exclude the ones that already had a failed verification
                USER.EMAIL.notIn(haveAlreadyFailedVerification)
            )
            .and(
                // exclude the ones in the "bounced emails" list
                USER.EMAIL.notIn(bouncedEmails)
            )
            .orderBy(USER.CREATION)
            .awaitMappedRecords()
    }

    /**
     * Case-insensitive and trim-insensitive
     */
    suspend fun findAutomatedVerifications(email: String, timeLimit: Duration): List<EmailVerification> {
        return dslContext
            .select()
            .from(EMAIL_VERIFICATION)
            .where(EMAIL_VERIFICATION.EMAIL.eqIgnoreCaseTrimmed(email))
            .and(EMAIL_VERIFICATION.VERIFICATION_TIME.isWithin(timeLimit))
            .orderBy(EMAIL_VERIFICATION.VERIFICATION_TIME)
            .awaitMappedRecords()
    }

    /**
     * Emails for which the more recent verification is "ok" or "ok_for_all"
     */
    suspend fun listAllAutomaticallyValidatedEmails(timeLimit: Duration): List<String> {
        return dslContext
            .select()
            .from(EMAIL_VERIFICATION)
            .where(EMAIL_VERIFICATION.VERIFICATION_TIME.isWithin(timeLimit))
            .awaitMappedRecords<EmailVerification>()
            .groupBy { record -> record.email }
            .map { (email, records) ->
                email to records.maxBy { record -> record.verificationTime }
            }
            .filter { (_, record) -> record.result == "ok" || record.result == "ok_for_all" }
            .map { (email, _) -> email }
            .toList()
    }

}
