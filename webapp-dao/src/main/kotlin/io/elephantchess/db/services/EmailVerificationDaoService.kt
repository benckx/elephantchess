package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.*
import io.elephantchess.db.dao.codegen.tables.daos.EmailVerificationDao
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerification
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerificationBounce
import io.elephantchess.db.utils.*
import io.elephantchess.model.SuppressingEmailStatus
import io.elephantchess.utils.di.KoinSingleton
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Duration

@KoinSingleton
class EmailVerificationDaoService(private val dslContext: DSLContext) {

    suspend fun save(record: EmailVerification) {
        dslContext.transactionCoroutine { cfg ->
            val cleanRecord = EmailVerification(record).apply { email = email.trim() }

            EmailVerificationDao(cfg)
                .insertReactive(cleanRecord)
        }
    }

    suspend fun listAllBouncedEmailsRecords(): List<EmailVerificationBounce> =
        dslContext
            .selectFrom(EMAIL_VERIFICATION_BOUNCE)
            .orderBy(
                EMAIL_VERIFICATION_BOUNCE.CREATION_TIME.desc(),
                EMAIL_VERIFICATION_BOUNCE.EMAIL,
            )
            .awaitMappedRecords()

    /**
     * Filtered in for some "enhanced delivery-status codes"
     */
    suspend fun listBouncedEmails(): List<String> =
        dslContext
            .select(EMAIL_VERIFICATION_BOUNCE.EMAIL)
            .from(EMAIL_VERIFICATION_BOUNCE)
            .where(suppressingBounceCondition())
            .awaitMappedRecords()

    suspend fun hasEmailBounced(email: String): Boolean =
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

    private fun suppressingBounceCondition() =
        EMAIL_VERIFICATION_BOUNCE.STATUS.isNull
            .or(EMAIL_VERIFICATION_BOUNCE.STATUS.`in`(SuppressingEmailStatus.codes))
}
