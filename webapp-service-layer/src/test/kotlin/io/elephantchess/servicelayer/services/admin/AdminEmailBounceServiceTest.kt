package io.elephantchess.servicelayer.services.admin

import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerificationBounce
import io.elephantchess.db.services.EmailVerificationDaoService
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class AdminEmailBounceServiceTest {

    @Test
    fun `admin response includes metadata suppression without pagination`() =
        runTest {
            val dao = mock<EmailVerificationDaoService>()
            val time = Instant.parse("2026-11-10T12:00:00Z")
            val records = (0..100).map {
                EmailVerificationBounce().apply {
                    email = "recipient$it@example.org"
                    bouncedTime = time
                    action = "failed"
                    status = when (it) {
                        0 -> "5.1.1"
                        1 -> null
                        else -> "5.7.1"
                    }
                    diagnostic = "smtp; 550 <policy rejection>"
                }
            }
            whenever(dao.listBounceRecords(Int.MAX_VALUE, 0)).thenReturn(records)
            val response = AdminEmailBounceService(dao).listBounces()
            assertEquals(101, response.entries.size)
            assertTrue(response.entries[0].suppressed)
            assertTrue(response.entries[1].suppressed)
            assertFalse(response.entries[2].suppressed)
            assertEquals("5.7.1", response.entries[2].status)
            assertEquals("failed", response.entries[2].action)
            assertEquals(time.toEpochMilliseconds(), response.entries[2].bouncedTime)
            assertEquals("smtp; 550 <policy rejection>", response.entries[2].diagnostic)
        }
}
