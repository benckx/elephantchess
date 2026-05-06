package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.NEWSLETTER
import io.elephantchess.db.dao.codegen.Tables.NEWSLETTER_EMAIL
import io.elephantchess.db.dao.codegen.tables.daos.NewsletterEmailDao
import io.elephantchess.db.dao.codegen.tables.pojos.Newsletter
import io.elephantchess.db.dao.codegen.tables.pojos.NewsletterEmail
import io.elephantchess.db.model.NewsletterStatsRecord
import io.elephantchess.db.utils.*
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine
import kotlin.time.Clock
import kotlin.time.Instant

class NewsletterDaoService(private val dslContext: DSLContext) {

    /**
     * NEWSLETTER for which no NEWSLETTER_EMAIL entry exists yet
     * (regardless of send date)
     *
     * User who signed up after the newsletter has been fanned out will not receive the newsletter
     */
    suspend fun listNewslettersToFanOut(): List<String> {
        return dslContext
            .selectDistinct(NEWSLETTER.ID)
            .from(NEWSLETTER)
            .where(
                NEWSLETTER.ID.notIn(
                    dslContext
                        .selectDistinct(NEWSLETTER.ID)
                        .from(NEWSLETTER, NEWSLETTER_EMAIL)
                        .where(NEWSLETTER.ID.eq(NEWSLETTER_EMAIL.NEWSLETTER_ID))
                )
            )
            .awaitMappedRecords()
    }

    suspend fun findNewsletterById(newsletterId: String): Newsletter? {
        return dslContext
            .select()
            .from(NEWSLETTER)
            .where(NEWSLETTER.ID.eq(newsletterId))
            .awaitMappedRecords<Newsletter>()
            .firstOrNull()
    }

    suspend fun insertAll(records: List<NewsletterEmail>) {
        dslContext.transactionCoroutine { cfg ->
            NewsletterEmailDao(cfg).insertMultipleReactive(records)
        }
    }

    suspend fun getBatchToSend(size: Int): List<NewsletterEmail> {
        return dslContext
            .select()
            .from(NEWSLETTER_EMAIL)
            .where(NEWSLETTER_EMAIL.SENT_TIME.isNull)
            .orderBy(NEWSLETTER_EMAIL.CREATED_TIME)
            .limit(size)
            .awaitMappedRecords()
    }

    suspend fun getLastSentNewsLetterEmail(): Instant? {
        return dslContext
            .select(NEWSLETTER_EMAIL.SENT_TIME)
            .from(NEWSLETTER_EMAIL)
            .where(NEWSLETTER_EMAIL.SENT_TIME.isNotNull)
            .orderBy(NEWSLETTER_EMAIL.SENT_TIME.desc())
            .limit(1)
            .awaitSingleValue()
    }

    suspend fun markAsSent(newsletterId: String, emailAddress: String) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(NEWSLETTER_EMAIL)
                .set(NEWSLETTER_EMAIL.SENT_TIME.fixed(), Clock.System.now())
                .where(NEWSLETTER_EMAIL.NEWSLETTER_ID.eq(newsletterId))
                .and(NEWSLETTER_EMAIL.EMAIL_ADDRESS.eq(emailAddress))
                .awaitExecute()
        }
    }

    suspend fun findByCode(code: String): NewsletterEmail? {
        return dslContext
            .select()
            .from(NEWSLETTER_EMAIL)
            .where(NEWSLETTER_EMAIL.UNSUBSCRIBE_FROM_NEWSLETTER_CODE.eq(code))
            .or(NEWSLETTER_EMAIL.UNSUBSCRIBE_FROM_ALL_CODE.eq(code))
            .awaitMappedRecords<NewsletterEmail>()
            .firstOrNull()
    }

    suspend fun markAsUnsubscribedFromNewsletter(code: String) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(NEWSLETTER_EMAIL)
                .set(NEWSLETTER_EMAIL.UNSUBSCRIBED_FROM_NEWSLETTER.fixed(), Clock.System.now())
                .where(NEWSLETTER_EMAIL.UNSUBSCRIBE_FROM_NEWSLETTER_CODE.eq(code))
                .awaitExecute()
        }
    }

    suspend fun markAsUnsubscribedFromAllEmailNotifications(code: String) {
        dslContext.transactionCoroutine { cfg ->
            DSL
                .using(cfg)
                .update(NEWSLETTER_EMAIL)
                .set(NEWSLETTER_EMAIL.UNSUBSCRIBED_FROM_ALL.fixed(), Clock.System.now())
                .where(NEWSLETTER_EMAIL.UNSUBSCRIBE_FROM_ALL_CODE.eq(code))
                .awaitExecute()
        }
    }

    suspend fun fetchNewsletterStats(): List<NewsletterStatsRecord> {
        return dslContext
            .select(
                NEWSLETTER.ID,
                NEWSLETTER.TEMPLATE_NAME,
                NEWSLETTER.SUBJECT,
                DSL.min(NEWSLETTER_EMAIL.CREATED_TIME).`as`("created_time"),
                DSL.min(NEWSLETTER_EMAIL.SENT_TIME).`as`("first_sent_time"),
                DSL.max(NEWSLETTER_EMAIL.SENT_TIME).`as`("last_sent_time"),
                DSL.count(NEWSLETTER_EMAIL.EMAIL_ADDRESS).`as`("total_emails"),
                DSL.count(NEWSLETTER_EMAIL.SENT_TIME).`as`("sent_count"),
                DSL.count(NEWSLETTER_EMAIL.UNSUBSCRIBED_FROM_NEWSLETTER).`as`("unsubscribed_newsletter_count"),
                DSL.count(NEWSLETTER_EMAIL.UNSUBSCRIBED_FROM_ALL).`as`("unsubscribed_all_count")
            )
            .from(NEWSLETTER)
            .leftJoin(NEWSLETTER_EMAIL).on(NEWSLETTER.ID.eq(NEWSLETTER_EMAIL.NEWSLETTER_ID))
            .groupBy(NEWSLETTER.ID, NEWSLETTER.TEMPLATE_NAME, NEWSLETTER.SUBJECT)
            .orderBy(DSL.min(NEWSLETTER_EMAIL.CREATED_TIME).desc().nullsLast())
            .awaitRecords()
            .map { record ->
                NewsletterStatsRecord(
                    newsletterId = record.get(NEWSLETTER.ID),
                    templateName = record.get(NEWSLETTER.TEMPLATE_NAME),
                    subject = record.get(NEWSLETTER.SUBJECT),
                    createdTime = record.get("created_time", Instant::class.java),
                    firstSentTime = record.get("first_sent_time", Instant::class.java),
                    lastSentTime = record.get("last_sent_time", Instant::class.java),
                    totalEmails = record.get("total_emails", Int::class.java) ?: 0,
                    sentCount = record.get("sent_count", Int::class.java) ?: 0,
                    unsubscribedNewsletterCount = record.get("unsubscribed_newsletter_count", Int::class.java) ?: 0,
                    unsubscribedAllCount = record.get("unsubscribed_all_count", Int::class.java) ?: 0
                )
            }
    }

}
