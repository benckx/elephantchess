package io.elephantchess.db

import io.elephantchess.db.codegen.LocalDateTimeInstantConverter
import io.elephantchess.db.dao.codegen.Tables.EMAIL_VERIFICATION_BOUNCE
import io.elephantchess.db.dao.codegen.Tables.PUZZLE
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class LocalDateTimeInstantConverterTest {
    private val converter = LocalDateTimeInstantConverter()

    @Test
    fun `timezone-free timestamps round trip as UTC with nanosecond precision`() {
        val timestamp = LocalDateTime.parse("2026-11-11T08:05:19.123456789")
        val instant = Instant.parse("2026-11-11T08:05:19.123456789Z")

        assertEquals(instant, converter.from(timestamp))
        assertEquals(timestamp, converter.to(instant))
    }

    @Test
    fun `null timestamps remain null`() {
        assertNull(converter.from(null))
        assertNull(converter.to(null))
    }

    @Test
    fun `generated timestamp fields use Instant regardless of timezone storage`() {
        assertEquals(Instant::class.java, EMAIL_VERIFICATION_BOUNCE.CREATION_TIME.type)
        assertEquals(Instant::class.java, EMAIL_VERIFICATION_BOUNCE.UPDATE_TIME.type)
        assertEquals(Instant::class.java, PUZZLE.CREATED_AT.type)
    }
}
