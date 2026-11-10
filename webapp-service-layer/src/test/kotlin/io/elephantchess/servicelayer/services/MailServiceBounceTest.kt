package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.tables.pojos.User
import io.elephantchess.db.services.EmailVerificationDaoService
import io.elephantchess.db.services.NewsletterDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.servicelayer.clients.EmailListVerifyClient
import io.elephantchess.servicelayer.dto.user.EmailValidityStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock

class MailServiceBounceTest {

    private val config = mock<AppConfig> {
        on { webHost } doReturn "https://example.org"
        on { sendMailNotifications } doReturn true
    }
    private val dao = mock<EmailVerificationDaoService>()
    private val users = mock<UserDaoService>()
    private val renderer = mock<MailRenderer>()
    private val mail = MailService(
        config,
        dao,
        mock<NewsletterDaoService>(),
        users,
        mock<EmailListVerifyClient>(),
        renderer,
        KotlinLogging.logger {},
    )
    private val email = "recipient@example.org"

    @Test
    fun `suppressing bounce takes precedence over manual confirmation`() =
        runTest {
            whenever(dao.hasSuppressingBounce(email)).thenReturn(true)
            assertEquals(EmailValidityStatus.AUTOMATED_BOUNCED, mail.getEmailValidityDetails(email))
            verifyNoInteractions(users)
        }

    @Test
    fun `non suppressing bounce preserves manually confirmed status`() =
        runTest {
            whenever(dao.hasSuppressingBounce(email)).thenReturn(false)
            whenever(users.findByEmail(email)).thenReturn(User().apply { emailConfirmedAt = Clock.System.now() })
            assertEquals(EmailValidityStatus.MANUALLY_CONFIRMED, mail.getEmailValidityDetails(email))
        }

    @Test
    fun `confirmation mail cannot bypass suppressing bounce`() =
        runTest {
            whenever(dao.hasSuppressingBounce(email)).thenReturn(true)
            mail.sendEmailConfirmation(email, "code", true)
            verifyNoInteractions(renderer)
        }

    @Test
    fun `direct newsletter send cannot bypass suppressing bounce`() =
        runTest {
            whenever(dao.hasSuppressingBounce(email)).thenReturn(true)
            assertFalse(mail.sendNewsLetter(email, "template", "subject", "newsletter-code", "all-code"))
            verifyNoInteractions(renderer)
        }

    @Test
    fun `newsletter recipients exclude suppressing bounces ignoring case and whitespace`() =
        runTest {
            whenever(dao.listAllAutomaticallyValidatedEmails(any())).thenReturn(emptyList())
            whenever(users.listManuallyConfirmedEmailAddresses()).thenReturn(listOf(email, "allowed@example.org"))
            whenever(users.listNewsletterEmailAddresses()).thenReturn(listOf(email, "allowed@example.org"))
            whenever(dao.listSuppressedEmails()).thenReturn(listOf(" RECIPIENT@EXAMPLE.ORG "))

            assertEquals(listOf("allowed@example.org"), mail.listNewsLetterRecipientEmails())
        }
}
