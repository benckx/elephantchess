package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.tables.pojos.EmailVerification
import io.elephantchess.db.dao.codegen.tables.pojos.User
import io.elephantchess.db.services.EmailVerificationDaoService
import io.elephantchess.db.services.NewsletterDaoService
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.generateId
import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.htmlrenderer.TagResolver
import io.elephantchess.model.EmailVerifierService.EMAIL_LIST_VERIFY
import io.elephantchess.servicelayer.clients.EmailListVerifyClient
import io.elephantchess.servicelayer.dto.user.EmailValidityStatus
import io.elephantchess.servicelayer.model.Email
import io.elephantchess.servicelayer.model.MatchingEmail
import io.elephantchess.servicelayer.model.UserId
import io.elephantchess.servicelayer.services.UserService.Companion.PASSWORD_RECOVERY_TIMEOUT_HOURS
import io.elephantchess.servicelayer.services.resolvers.EmailConfirmationLinkTagResolver
import io.elephantchess.servicelayer.services.resolvers.GameLinkTagResolver
import io.elephantchess.servicelayer.services.resolvers.RecoveryLinkTagResolver
import io.elephantchess.servicelayer.services.resolvers.UnsubscribeTagResolver
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.activation.DataHandler
import javax.activation.URLDataSource
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class MailService(
    private val appConfig: AppConfig,
    private val emailVerificationDaoService: EmailVerificationDaoService,
    private val newsletterDaoService: NewsletterDaoService,
    private val userDaoService: UserDaoService,
    private val verifyClient: EmailListVerifyClient,
    private val mailRenderer: MailRenderer,
    private val logger: KLogger,
) {

    private val sendMailNotifications by lazy { appConfig.sendMailNotifications }

    // mail configuration
    private val mailConfig by lazy { appConfig.mailConfig }

    // templating
    private val webHost = appConfig.webHost

    private val mailScope by lazy { CoroutineScope(Dispatchers.IO) }

    suspend fun isEmailAddressValid(email: String): Boolean {
        return getEmailValidityDetails(email).isValid
    }

    /**
     * Returns the full [EmailValidityStatus] for the given email.
     *
     * The check is layered: if a user owns this address and has clicked the confirmation
     * link sent at signup, the address is considered [EmailValidityStatus.MANUALLY_CONFIRMED]
     * (the strongest signal). Otherwise, we fall back to the automated verification.
     */
    suspend fun getEmailValidityDetails(email: String): EmailValidityStatus {
        val user = userDaoService.findByEmail(email)
        if (user?.emailConfirmedAt != null) {
            return EmailValidityStatus.MANUALLY_CONFIRMED
        }

        if (emailVerificationDaoService.hasEmailBounced(email)) {
            return EmailValidityStatus.AUTOMATED_BOUNCED
        }

        val automatedVerifications =
            emailVerificationDaoService.findAutomatedVerifications(email, emailValidityDuration)

        return when (automatedVerifications.size) {
            0 -> EmailValidityStatus.UNKNOWN
            else -> {
                val result = automatedVerifications.last().result
                if (result == "ok" || result == "ok_for_all") {
                    EmailValidityStatus.AUTOMATED_VALID
                } else {
                    EmailValidityStatus.AUTOMATED_INVALID
                }
            }
        }
    }

    fun verifyEmailAddressAsync(email: String) {
        if (sendMailNotifications) {
            mailScope.launch {
                verifyEmailAddress(email)
            }
        } else {
            logger.debug { "not verifying emails because mustSendEmails is disabled" }
        }
    }

    suspend fun verifyEmailAddress(email: String) {
        try {
            if (sendMailNotifications) {
                val result = verifyClient.verifyEmailDetailed(email)
                if (result != null) {
                    logger.info { result }
                    val record = EmailVerification()
                    record.id = generateId()
                    record.email = email
                    record.result = result.result
                    record.verificationTime = Clock.System.now()
                    record.serviceName = EMAIL_LIST_VERIFY
                    emailVerificationDaoService.save(record)
                } else {
                    logger.warn { "null result for $email" }
                }
            } else {
                logger.info { "not verifying emails because mustSendEmails is disabled" }
            }
        } catch (e: Exception) {
            logger.error(e) { "error verifying email $email" }
        }
    }

    suspend fun unsubscribeFromEmailNotifications(code: String): MatchingEmail? {
        val matchingEmail = newsletterDaoService
            .findByCode(code)
            ?.let { record ->
                MatchingEmail(
                    emailAddress = record.emailAddress,
                    isUnsubscribeFromNewsletter = record.unsubscribeFromNewsletterCode == code,
                    isUnsubscribeFromAll = record.unsubscribeFromAllCode == code
                )
            }

        if (matchingEmail != null) {
            when {
                matchingEmail.isUnsubscribeFromNewsletter -> {
                    userDaoService.unsubscribeFromNewsletter(matchingEmail.emailAddress)
                    newsletterDaoService.markAsUnsubscribedFromNewsletter(code)
                }

                matchingEmail.isUnsubscribeFromAll -> {
                    userDaoService.unsubscribeFromAllEmailNotifications(matchingEmail.emailAddress)
                    newsletterDaoService.markAsUnsubscribedFromAllEmailNotifications(code)
                }
            }
        }

        return matchingEmail
    }

    /**
     * List all emails that:
     * - have a valid status (manually confirmed by the user, or automatically validated by the
     *   external service)
     * - have not bounced
     * - consent to receive the newsletter (implicit)
     */
    suspend fun listNewsLetterRecipientEmails(): List<String> {
        val automaticallyValidated =
            emailVerificationDaoService.listAllAutomaticallyValidatedEmails(emailValidityDuration)
        val manuallyConfirmed = userDaoService.listManuallyConfirmedEmailAddresses().toSet()
        val bounced = emailVerificationDaoService.listBouncedEmails()
        val newsletterRecipients = userDaoService
            .listNewsletterEmailAddresses()
            .filter { emailAddress ->
                manuallyConfirmed.contains(emailAddress) || automaticallyValidated.contains(emailAddress)
            }
            .filterNot { emailAddress -> bounced.contains(emailAddress) }
            .map { emailAddress -> emailAddress.trim() }

        logger.info {
            "found ${automaticallyValidated.size} automatically validated e-mail addresses, " +
                    "${manuallyConfirmed.size} manually confirmed e-mail addresses, " +
                    "${bounced.size} bounced e-mail addresses, " +
                    "${newsletterRecipients.size} total newsletter recipients"
        }

        return newsletterRecipients.sorted()
    }

    fun sendLowCreditNotification(available: Int) {
        if (sendMailNotifications) {
            sendMail(
                Email(
                    to = ADMIN_GMAIL_EMAIL,
                    subject = "Low credit on EmailListVerify",
                    body = "Available credits $available"
                )
            )
        } else {
            logger.info { "not sending low credit notification because mustSendEmails is disabled" }
        }
    }

    fun sendEnginePoolHealthCheckFailed(errors: List<String>) {
        if (sendMailNotifications) {
            sendMail(
                Email(
                    to = ADMIN_GMAIL_EMAIL,
                    subject = "Engine pool health check failed",
                    body = "The following engine(s) failed the health check:<br><br>${errors.joinToString("<br>")}"
                )
            )
        } else {
            logger.info { "not sending engine pool health check notification because mustSendEmails is disabled" }
        }
    }

    suspend fun sendContactForm(userId: UserId, email: String, message: String) {
        resolveAndSend(
            recipient = ADMIN_GMAIL_EMAIL,
            subject = "contact form",
            templateName = "contact_form_message",
            resolvers = listOf(
                SimpleValueTagResolver("user_id", userId.toString()),
                SimpleValueTagResolver("email", email),
                SimpleValueTagResolver("message", message),
            )
        )
    }

    suspend fun sendNewUserNotification(user: User, guestTransferred: Boolean) {
        resolveAndSend(
            recipient = ADMIN_GMAIL_EMAIL,
            subject = "new user",
            templateName = "new_user_notification",
            resolvers = listOf(
                SimpleValueTagResolver("username", user.handle),
                SimpleValueTagResolver("email", user.email),
                SimpleValueTagResolver("guest_transferred", if (guestTransferred) "yes" else "no")
            ),
            skipRecipientValidityCheck = true
        )
    }

    suspend fun sendEmailConfirmation(recipient: String, code: String, showWelcomeMessage: Boolean) {
        val subject = if (showWelcomeMessage) {
            "Welcome to elephantchess - email address confirmation"
        } else {
            "elephantchess - email address confirmation"
        }

        resolveAndSend(
            recipient = recipient,
            subject = subject,
            templateName = "email_confirmation",
            resolvers = listOf(
                EmailConfirmationLinkTagResolver(webHost, code)
            ),
            // The whole point of this email is to flip the recipient's status to MANUALLY_CONFIRMED,
            // so we must not gate it on the address already being known-valid (it never is at signup).
            skipRecipientValidityCheck = true,
        )
    }

    suspend fun sendPasswordRecoveryAttempt(recipient: String, code: String) {
        resolveAndSend(
            recipient = recipient,
            subject = "Password Recovery",
            templateName = "password_recovery_attempt",
            resolvers = listOf(
                RecoveryLinkTagResolver(webHost, recipient, code),
                SimpleValueTagResolver("recovery_time_validity", "${PASSWORD_RECOVERY_TIMEOUT_HOURS}h")
            )
        )
    }

    suspend fun sendPasswordRecoverySuccessful(recipient: String) {
        resolveAndSend(
            recipient = recipient,
            subject = "Password Recovery Successful",
            templateName = "successful_password_recovery"
        )
    }

    suspend fun sendUserJoinedGameWhileOffline(recipient: String, inviteeUsername: String, gameId: String) {
        resolveAndSend(
            recipient = recipient,
            subject = "$inviteeUsername joined your game",
            templateName = "user_joined_game_while_offline",
            resolvers = listOf(
                SimpleValueTagResolver("invitee", inviteeUsername),
                GameLinkTagResolver(webHost, gameId)
            ),
            copyToAdmin = true
        )
    }

    // we could also indicate whether he lost
    suspend fun sendOpponentPlayedMoveWhileOffline(recipient: String, opponent: String, gameId: String) {
        resolveAndSend(
            recipient = recipient,
            subject = "$opponent played a move",
            templateName = "opponent_played_move_while_offline",
            resolvers = listOf(
                SimpleValueTagResolver("opponent", opponent),
                GameLinkTagResolver(webHost, gameId)
            ),
            copyToAdmin = true
        )
    }

    suspend fun sendOpponentResignedWhileOffline(recipient: String, opponent: String, gameId: String) {
        resolveAndSend(
            recipient = recipient,
            subject = "$opponent resigned",
            templateName = "opponent_resigned_while_offline",
            resolvers = listOf(
                SimpleValueTagResolver("opponent", opponent),
                GameLinkTagResolver(webHost, gameId)
            ),
            copyToAdmin = true
        )
    }

    suspend fun sendDrawProposedWhileOffline(recipient: String, opponent: String, gameId: String) {
        resolveAndSend(
            recipient = recipient,
            subject = "$opponent proposed a draw",
            templateName = "opponent_proposed_draw_while_offline",
            resolvers = listOf(
                SimpleValueTagResolver("opponent", opponent),
                GameLinkTagResolver(webHost, gameId)
            ),
            copyToAdmin = true
        )
    }

    suspend fun sendOpponentRespondedToDrawWhileOffline(
        recipient: String,
        opponent: String,
        accepted: Boolean,
        gameId: String,
    ) {
        val verb1 = if (accepted) "accepted" else "declined"
        val verb2 = if (accepted) "Review" else "Resume"

        resolveAndSend(
            recipient = recipient,
            subject = "$opponent $verb1 the draw",
            templateName = "opponent_responded_to_draw_while_offline",
            resolvers = listOf(
                SimpleValueTagResolver("opponent", opponent),
                SimpleValueTagResolver("verb1", verb1),
                SimpleValueTagResolver("verb2", verb2),
                GameLinkTagResolver(webHost, gameId)
            ),
            copyToAdmin = true
        )
    }

    /**
     * Asynchronous and safe
     */
    private suspend fun resolveAndSend(
        recipient: String,
        subject: String,
        templateName: String,
        resolvers: List<TagResolver> = listOf(),
        copyToAdmin: Boolean = false,
        skipRecipientValidityCheck: Boolean = false,
    ) {
        fun sendSafeAsync(email: Email) {
            mailScope.launch {
                try {
                    sendMail(email)
                } catch (e: Exception) {
                    logger.error(e) { "error sending email '${email.subject}" }
                }
            }
        }

        if (!sendMailNotifications) {
            logger.info { "not sending e-mail '$subject' because emails are disabled" }
            return
        }

        if (!skipRecipientValidityCheck && !isEmailAddressValid(recipient)) {
            logger.debug { "not sending e-mail '$subject' because recipient $recipient is not valid" }
            return
        }

        sendSafeAsync(
            Email(
                to = recipient,
                bcc = if (copyToAdmin) ADMIN_GMAIL_EMAIL else null,
                subject = subject,
                body = mailRenderer.renderEmail(templateName, resolvers)
            )
        )
    }

    /**
     * Synchronous and unsafe
     *
     * Automatically parses the rendered HTML body for CID references (src="cid:image-name")
     * and attaches the corresponding images from /newsletters/{templateName}/ folder.
     */
    suspend fun sendNewsLetter(
        recipient: String,
        templateName: String,
        subject: String,
        unsubscribeFromNewsletterCode: String,
        unsubscribeFromAllCode: String,
    ) {
        if (sendMailNotifications) {
            val renderedBody =
                mailRenderer.renderNewsLetter(
                    templateName = templateName,
                    specificTagResolvers = listOf(
                        UnsubscribeTagResolver(
                            appConfig,
                            "unsubscribe_from_newsletter_url",
                            unsubscribeFromNewsletterCode
                        ),
                        UnsubscribeTagResolver(
                            appConfig,
                            "unsubscribe_from_all_url",
                            unsubscribeFromAllCode
                        ),
                        SimpleValueTagResolver(
                            "utm_medium",
                            "newsletter-$templateName"
                        ),
                    )
                )

            // parse the rendered body for CID references and build embedded images map
            val imagesToEmbed = parseCidReferences(renderedBody, templateName)

            sendMail(
                Email(
                    to = recipient,
                    subject = subject,
                    body = renderedBody,
                    embeddedImages = imagesToEmbed
                )
            )
        } else {
            logger.info { "not sending newsletter because sendMailNotifications is disabled" }
        }
    }

    private fun sendMail(email: Email) {
        logger.debug { "sending to '${email.to}': $email" }
        val message = MimeMessage(session())
        message.setFrom(InternetAddress(mailConfig.username, "elephantchess.io"))
        message.addRecipient(Message.RecipientType.TO, InternetAddress(email.to))
        email.cc?.let { cc -> message.addRecipient(Message.RecipientType.CC, InternetAddress(cc)) }
        email.bcc?.let { bcc -> message.addRecipient(Message.RecipientType.BCC, InternetAddress(bcc)) }
        message.subject = email.subject

        if (email.embeddedImages.isEmpty()) {
            message.setContent(email.body, "text/html; charset=utf-8")
        } else {
            val multipart = MimeMultipart("related")

            // HTML body part
            val htmlPart = MimeBodyPart()
            htmlPart.setContent(email.body, "text/html; charset=utf-8")
            multipart.addBodyPart(htmlPart)

            // embedded images
            email.embeddedImages.forEach { (contentId, resourcePath) ->
                val imagePart = MimeBodyPart()
                val imageUrl = this::class.java.getResource(resourcePath)
                    ?: throw IllegalArgumentException("Resource not found: $resourcePath")
                val dataSource = URLDataSource(imageUrl)
                imagePart.dataHandler = DataHandler(dataSource)
                imagePart.setHeader("Content-ID", "<$contentId>")
                imagePart.disposition = MimeBodyPart.INLINE
                multipart.addBodyPart(imagePart)
            }

            message.setContent(multipart)
        }

        Transport.send(message)
    }

    private fun session(): Session {
        val sysProperties = System.getProperties()
        sysProperties["mail.smtp.host"] = mailConfig.smtpHost
        sysProperties["mail.smtp.port"] = mailConfig.smtpPort
        sysProperties["mail.smtp.ssl.enable"] = mailConfig.smtpSslEnable
        sysProperties["mail.smtp.auth"] = mailConfig.smtpAuth

        val session =
            Session.getInstance(sysProperties, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(mailConfig.username, mailConfig.password)
                }
            })

        session.debug = false
        return session
    }

    /**
     * Parses the HTML body for CID references (e.g., src="cid:image-name")
     * and returns a map of Content-ID to resource path.
     *
     * Images are expected to be in /newsletters/{templateName}/ folder with .png extension.
     */
    private fun parseCidReferences(htmlBody: String, templateName: String): Map<String, String> {
        val cidPattern = """src="cid:([^"]+)"""".toRegex()
        return cidPattern.findAll(htmlBody)
            .map { it.groupValues[1] }
            .distinct()
            .associateWith { contentId -> "/newsletters/$templateName/$contentId.png" }
    }

    companion object {

        private const val ADMIN_GMAIL_EMAIL = "benoit.vleminckx@gmail.com"

        // 18 months
        val emailValidityDuration = (18 * 30).days

        // 12 months
        val emailValidityUpdateTime = (12 * 30).days

    }

}
