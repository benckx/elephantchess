package io.elephantchess.db.services

import io.elephantchess.db.dao.codegen.Tables.UTM_MEDIUM_CLICK
import io.elephantchess.db.utils.awaitRecords
import org.jooq.DSLContext
import org.jooq.impl.DSL

class UtmMediumClickDaoService(private val dslContext: DSLContext) {

    /**
     * Returns a map of newsletter ID to click count for all newsletters.
     * The utm_medium format is "newsletter-<newsletterId>".
     */
    suspend fun countClicksPerNewsletter(): Map<String, Int> {
        return dslContext
            .select(
                UTM_MEDIUM_CLICK.UTM_MEDIUM,
                DSL.count().`as`("click_count")
            )
            .from(UTM_MEDIUM_CLICK)
            .where(UTM_MEDIUM_CLICK.UTM_MEDIUM.startsWith("newsletter-"))
            .groupBy(UTM_MEDIUM_CLICK.UTM_MEDIUM)
            .awaitRecords()
            .associate { record ->
                val utmMedium = record.get(UTM_MEDIUM_CLICK.UTM_MEDIUM)
                val newsletterId = utmMedium.removePrefix("newsletter-")
                val clickCount = record.get("click_count", Int::class.java) ?: 0
                newsletterId to clickCount
            }
    }

}
