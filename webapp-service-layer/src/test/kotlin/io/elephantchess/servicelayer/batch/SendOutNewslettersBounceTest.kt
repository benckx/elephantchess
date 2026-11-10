package io.elephantchess.servicelayer.batch

import io.elephantchess.db.dao.codegen.tables.pojos.Newsletter
import io.elephantchess.db.dao.codegen.tables.pojos.NewsletterEmail
import io.elephantchess.db.services.NewsletterDaoService
import io.elephantchess.servicelayer.services.MailService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.*

class SendOutNewslettersBounceTest {

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `only actual sends are marked sent`(sent: Boolean) =
        runTest {
            val dao = mock<NewsletterDaoService>()
            val mail = mock<MailService>()
            val entry = NewsletterEmail().apply {
                newsletterId = "newsletter"
                emailAddress = "recipient@example.org"
                unsubscribeFromNewsletterCode = "newsletter-code"
                unsubscribeFromAllCode = "all-code"
            }
            whenever(dao.listNewslettersToFanOut()).thenReturn(emptyList())
            whenever(dao.getBatchToSend(2)).thenReturn(listOf(entry))
            whenever(dao.findNewsletterById("newsletter")).thenReturn(
                Newsletter().apply {
                    templateName = "template"
                    subject = "subject"
                },
            )
            whenever(mail.sendNewsLetter(entry.emailAddress, "template", "subject", "newsletter-code", "all-code")).thenReturn(sent)

            SendOutNewslettersBatch(KotlinLogging.logger {}, dao, mail).run()

            verify(mail).sendNewsLetter(entry.emailAddress, "template", "subject", "newsletter-code", "all-code")
            verify(dao, times(if (sent) 1 else 0)).markAsSent("newsletter", entry.emailAddress)
        }
}
