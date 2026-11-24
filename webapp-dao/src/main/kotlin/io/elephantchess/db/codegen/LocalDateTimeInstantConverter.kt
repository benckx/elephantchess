package io.elephantchess.db.codegen

import org.jooq.impl.AbstractConverter
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/** Maps timezone-free database timestamps to instants, interpreting and writing wall-clock values as UTC. */
class LocalDateTimeInstantConverter :
    AbstractConverter<LocalDateTime, Instant>(
        LocalDateTime::class.java,
        Instant::class.java,
    ) {
    override fun from(databaseObject: LocalDateTime?): Instant? =
        databaseObject?.toInstant(ZoneOffset.UTC)?.toKotlinInstant()

    override fun to(userObject: Instant?): LocalDateTime? =
        userObject?.toJavaInstant()?.atOffset(ZoneOffset.UTC)?.toLocalDateTime()
}
