package io.elephantchess.db.codegen

import org.jooq.impl.AbstractConverter
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * jOOQ converter that maps `TIMESTAMP WITH TIME ZONE` columns (delivered by the JDBC driver as
 * [java.time.OffsetDateTime]) to [kotlin.time.Instant] and back.
 *
 * Since the database column is timezone-aware, the offset carried by [OffsetDateTime] already
 * pins the absolute instant, so no zone assumption is needed. We always write back at
 * [ZoneOffset.UTC] for consistency.
 *
 * Wired by `build.gradle.kts`'s `dao-code-gen` task as a forced type for every `timestamptz` column.
 */
class OffsetDateTimeInstantConverter : AbstractConverter<OffsetDateTime, Instant>(
    OffsetDateTime::class.java,
    Instant::class.java,
) {
    override fun from(databaseObject: OffsetDateTime?): Instant? {
        return databaseObject
            ?.toInstant()
            ?.toKotlinInstant()
    }

    override fun to(userObject: Instant?): OffsetDateTime? {
        return userObject
            ?.toJavaInstant()
            ?.atOffset(ZoneOffset.UTC)
    }
}
