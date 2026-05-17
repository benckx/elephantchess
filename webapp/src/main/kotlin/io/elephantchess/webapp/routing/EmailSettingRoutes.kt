package io.elephantchess.webapp.routing

import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.services.MailService
import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.servicelayer.utils.obfuscateEmail
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val simplePageRenderer by koin<SimplePageRenderer>()

fun Application.emailSettingRoutes() {
    emailConfirmationRoutes()
    newsletterUnsubscriptionRoutes()
}

// renders a public page when the user clicks the confirmation link sent to them by email at signup.
private fun Application.emailConfirmationRoutes() {
    val userService by koin<UserService>()
    routing {
        get("/email/confirm") {
            val code = call.parameters["code"].orEmpty()
            val confirmed = userService.confirmEmail(code)
            val template = if (confirmed) {
                "email_confirmation/email_confirmed"
            } else {
                "email_confirmation/email_confirmation_error"
            }
            call.respondText(simplePageRenderer.renderTemplateNoCache(template), ContentType.Text.Html)
        }
    }
}


// when sending the newsletter, there is a link to unsubscribe, which contains a unique code that identifies
// the user and the type of unsubscription (newsletter only or all notifications).
private fun Application.newsletterUnsubscriptionRoutes() {
    routing {
        get("/unsubscribe") {
            val code = call.parameters["code"] ?: throw BadRequestException("code not provided")
            call.respondText(processAndRenderUnsubscription(code), ContentType.Text.Html)
        }
    }
}

private suspend fun processAndRenderUnsubscription(code: String): String {
    val mailService by koin<MailService>()

    return when (val matching = mailService.unsubscribeFromEmailNotifications(code)) {
        null -> simplePageRenderer.renderTemplateNoCache("unsubscribe/unsubscription_error")
        else -> {
            val tagResolvers = listOf(
                SimpleValueTagResolver(
                    "emailAddress",
                    obfuscateEmail(matching.emailAddress)
                )
            )

            when {
                matching.isUnsubscribeFromNewsletter -> {
                    simplePageRenderer.renderTemplateNoCache(
                        "unsubscribe/unsubscribed_from_newsletter",
                        tagResolvers
                    )
                }

                matching.isUnsubscribeFromAll -> {
                    simplePageRenderer.renderTemplateNoCache(
                        "unsubscribe/unsubscribed_from_all",
                        tagResolvers
                    )
                }

                else -> simplePageRenderer.renderTemplateNoCache("unsubscribe/unsubscription_error")
            }
        }
    }
}
