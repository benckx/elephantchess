package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.EmailVerificationBounceDao
import io.elephantchess.db.dao.codegen.tables.daos.EmailVerificationDao
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerification
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerificationBounce
import io.elephantchess.db.utils.*
import io.elephantchess.model.SuppressingEmailStatus
import io.elephantchess.utils.di.KoinSingleton
import io.github.oshai.kotlinlogging.KLogger
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Duration

@KoinSingleton
class EmailVerificationDaoService(private val dslContext: DSLContext, private val logger: KLogger) {

    suspend fun save(record: EmailVerification) {
        dslContext.transactionCoroutine { cfg ->
            EmailVerificationDao(cfg).insertReactive(
                EmailVerification(record).apply { email = email.trim() },
            )
        }
    }

    // TODO: Remove this private-script import method once automated bounce ingestion is deployed.
    suspend fun insertOrUpdateBounced(records: List<EmailVerificationBounce>) {
        dslContext.transactionCoroutine { cfg ->
            records.forEach { input ->
                val record = EmailVerificationBounce(input).apply { email = email.trim() }
                val found = DSL
                    .using(cfg)
                    .selectFrom(EMAIL_VERIFICATION_BOUNCE)
                    .where(EMAIL_VERIFICATION_BOUNCE.EMAIL.eq(record.email))
                    .awaitMappedRecords<EmailVerificationBounce>().firstOrNull()

                if (found == null) {
                    EmailVerificationBounceDao(cfg).insertReactive(record)
                    logger.info { "bounced email inserted $record" }
                } else if (record.status == null && found.status != null) {
                    logger.info { "ignoring legacy bounce without status for ${record.email}; delivery metadata already exists" }
                } else if (record.bouncedTime >= found.bouncedTime && record != found) {
                    DSL.using(cfg)
                        .update(EMAIL_VERIFICATION_BOUNCE)
                        .set(EMAIL_VERIFICATION_BOUNCE.BOUNCED_TIME, record.bouncedTime)
                        .set(EMAIL_VERIFICATION_BOUNCE.ACTION, record.action)
                        .set(EMAIL_VERIFICATION_BOUNCE.STATUS, record.status)
                        .set(EMAIL_VERIFICATION_BOUNCE.DIAGNOSTIC, record.diagnostic)
                        .where(EMAIL_VERIFICATION_BOUNCE.EMAIL.eq(found.email))
                        .awaitExecute()
                    logger.info { "bounced email updated $record" }
                } else {
                    logger.info { "ignoring unchanged or older bounce for ${record.email}" }
                }
            }
        }
    }

    suspend fun listBouncedEmails(): List<String> =
        dslContext
            .select(EMAIL_VERIFICATION_BOUNCE.EMAIL)
            .from(EMAIL_VERIFICATION_BOUNCE)
            .orderBy(EMAIL_VERIFICATION_BOUNCE.BOUNCED_TIME)
            .awaitMappedRecords()

    suspend fun listBounceRecords(limit: Int, offset: Int): List<EmailVerificationBounce> =
        dslContext
            .selectFrom(EMAIL_VERIFICATION_BOUNCE)
            .orderBy(EMAIL_VERIFICATION_BOUNCE.BOUNCED_TIME.desc(), EMAIL_VERIFICATION_BOUNCE.EMAIL)
            .limit(limit)
            .offset(offset)
            .awaitMappedRecords()

    suspend fun listSuppressedEmails(): List<String> =
        dslContext
            .select(EMAIL_VERIFICATION_BOUNCE.EMAIL)
            .from(EMAIL_VERIFICATION_BOUNCE)
            .where(suppressingBounceCondition())
            .awaitMappedRecords()

    suspend fun hasSuppressingBounce(email: String): Boolean =
        dslContext
            .select(EMAIL_VERIFICATION_BOUNCE.EMAIL)
            .from(EMAIL_VERIFICATION_BOUNCE)
            .where(EMAIL_VERIFICATION_BOUNCE.EMAIL.eq(email))
            .and(suppressingBounceCondition())
            .awaitSingleValue<String>() != null

    suspend fun listEmailsToVerify(limit: Duration): List<String> {
        val verifiedRecently = dslContext
            .selectDistinct(EMAIL_VERIFICATION.EMAIL)
            .from(EMAIL_VERIFICATION)
            .where(
                EMAIL_VERIFICATION.VERIFICATION_TIME.isWithin(limit),
            )

        val haveAlreadyFailedVerification = dslContext
            .selectDistinct(EMAIL_VERIFICATION.EMAIL)
            .from(EMAIL_VERIFICATION)
            .where(
                EMAIL_VERIFICATION.RESULT.notIn("ok", "ok_for_all"),
            )

        val bouncedEmails = dslContext
            .select(EMAIL_VERIFICATION_BOUNCE.EMAIL)
            .from(EMAIL_VERIFICATION_BOUNCE)
            .where(suppressingBounceCondition())

        return dslContext
            .select(USER.EMAIL)
            .from(USER)
            .where(USER.EMAIL.isNotNull)
            .and(
                // exclude the ones verified not long ago
                USER.EMAIL.notIn(verifiedRecently),
            )
            .and(
                // exclude the ones that already had a failed verification
                USER.EMAIL.notIn(haveAlreadyFailedVerification),
            )
            .and(
                // exclude the ones in the "bounced emails" list
                USER.EMAIL.notIn(bouncedEmails),
            )
            .orderBy(USER.CREATION)
            .awaitMappedRecords()
    }

    /**
     * Case-insensitive and trim-insensitive
     */
    suspend fun findAutomatedVerifications(email: String, timeLimit: Duration): List<EmailVerification> =
        dslContext
            .select()
            .from(EMAIL_VERIFICATION)
            .where(EMAIL_VERIFICATION.EMAIL.eqIgnoreCaseTrimmed(email))
            .and(EMAIL_VERIFICATION.VERIFICATION_TIME.isWithin(timeLimit))
            .orderBy(EMAIL_VERIFICATION.VERIFICATION_TIME)
            .awaitMappedRecords()

    /**
     * Emails for which the more recent verification is "ok" or "ok_for_all"
     */
    suspend fun listAllAutomaticallyValidatedEmails(timeLimit: Duration): List<String> =
        dslContext
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

internal fun suppressingBounceCondition() =
    EMAIL_VERIFICATION_BOUNCE.STATUS.isNull
        .or(EMAIL_VERIFICATION_BOUNCE.STATUS.`in`(SuppressingEmailStatus.codes))
