package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.KOFI_EVENT
import io.elephantchess.db.dao.codegen.tables.daos.KofiEventDao
import io.elephantchess.db.dao.codegen.tables.pojos.KofiEvent
import io.elephantchess.db.utils.awaitMappedRecords
import io.elephantchess.db.utils.awaitSingleMappedRecord
import io.elephantchess.db.utils.insertReactive
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.kotlin.coroutines.transactionCoroutine

class KofiEventDaoService(private val dslContext: DSLContext) {

    // fields mapped to Dto
    private val relevantFields = listOf(
        KOFI_EVENT.MATCHED_USER_ID,
        KOFI_EVENT.FROM_NAME,
        KOFI_EVENT.TIMESTAMP,
        KOFI_EVENT.AMOUNT,
        KOFI_EVENT.CURRENCY,
        KOFI_EVENT.TRANSACTION_TYPE
    )

    suspend fun insert(record: KofiEvent) {
        dslContext.transactionCoroutine { cfg ->
            KofiEventDao(cfg).insertReactive(record)
        }
    }

    suspend fun listLatestTippers(limit: Int): List<KofiEvent> {
        val latestTips = dslContext
            .select(
                KOFI_EVENT.MATCHED_USER_ID,
                DSL.max(KOFI_EVENT.TIMESTAMP).`as`("max_timestamp"),
                DSL.max(KOFI_EVENT.ID).`as`("max_id")
            )
            .from(KOFI_EVENT)
            .where(KOFI_EVENT.MATCHED_USER_ID.isNotNull())
            .and(KOFI_EVENT.TRANSACTION_TYPE.`in`("Donation", "Tip"))
            .and(KOFI_EVENT.EMAIL.notEqual(EXAMPLE_EMAIL))
            .groupBy(KOFI_EVENT.MATCHED_USER_ID)
            .orderBy(DSL.max(KOFI_EVENT.TIMESTAMP).desc())
            .limit(limit)
            .asTable("latest_tips")

        return dslContext
            .select(relevantFields)
            .from(KOFI_EVENT)
            .join(latestTips)
            .on(
                KOFI_EVENT.MATCHED_USER_ID.eq(latestTips.field(KOFI_EVENT.MATCHED_USER_ID))
                    .and(KOFI_EVENT.TIMESTAMP.eq(latestTips.field("max_timestamp", KOFI_EVENT.TIMESTAMP.dataType)))
                    .and(KOFI_EVENT.ID.eq(latestTips.field("max_id", KOFI_EVENT.ID.dataType)))
            )
            .orderBy(KOFI_EVENT.TIMESTAMP.desc())
            .awaitMappedRecords()
    }

    /**
     * We take the earliest subscription payment per user,
     * to avoid user unsubscribing and resubscribing to appear as new supporters.
     */
    suspend fun listLatestRecurrentSupporters(limit: Int): List<KofiEvent> {
        val earliestSubscriptions = dslContext
            .select(
                KOFI_EVENT.MATCHED_USER_ID,
                DSL.min(KOFI_EVENT.TIMESTAMP).`as`("min_timestamp")
            )
            .from(KOFI_EVENT)
            .where(KOFI_EVENT.MATCHED_USER_ID.isNotNull())
            .and(KOFI_EVENT.TRANSACTION_TYPE.`in`("Subscription"))
            .and(KOFI_EVENT.EMAIL.notEqual(EXAMPLE_EMAIL))
            .and(KOFI_EVENT.IS_FIRST_SUBSCRIPTION_PAYMENT.eq(true))
            .groupBy(KOFI_EVENT.MATCHED_USER_ID)
            .asTable("latest_subscriptions")

        return dslContext
            .select(relevantFields)
            .from(KOFI_EVENT)
            .join(earliestSubscriptions)
            .on(
                KOFI_EVENT.MATCHED_USER_ID.eq(earliestSubscriptions.field(KOFI_EVENT.MATCHED_USER_ID))
                    .and(
                        KOFI_EVENT.TIMESTAMP.eq(
                            earliestSubscriptions.field(
                                "min_timestamp",
                                KOFI_EVENT.TIMESTAMP.dataType
                            )
                        )
                    )
            )
            .orderBy(KOFI_EVENT.TIMESTAMP.desc())
            .limit(limit)
            .awaitMappedRecords()
    }

    suspend fun fetchLatestEvent(): KofiEvent? {
        return dslContext
            .select(relevantFields)
            .from(KOFI_EVENT)
            .where(KOFI_EVENT.EMAIL.notEqual(EXAMPLE_EMAIL))
            .and(
                KOFI_EVENT.TRANSACTION_TYPE.`in`("Donation", "Tip").or(
                    KOFI_EVENT.TRANSACTION_TYPE.`in`("Subscription")
                        .and(KOFI_EVENT.IS_FIRST_SUBSCRIPTION_PAYMENT.eq(true))
                )
            )
            .orderBy(KOFI_EVENT.TIMESTAMP.desc())
            .limit(1)
            .awaitSingleMappedRecord()
    }

    companion object {

        // used for testing purposes
        const val EXAMPLE_EMAIL = "jo.example@example.com"

    }

}
