package io.elephantchess.webapp.routing

import io.elephantchess.htmlrenderer.SimpleValueTagResolver
import io.elephantchess.servicelayer.exceptions.BadRequestException
import io.elephantchess.servicelayer.services.MailService
import io.elephantchess.servicelayer.utils.obfuscateEmail
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val simplePageRenderer by koin<SimplePageRenderer>()
private val mailService by koin<MailService>()

fun Application.newsletterUnsubscriptionRoutes() {
    routing {
        get("/unsubscribe") {
            val code = call.parameters["code"] ?: throw BadRequestException("code not provided")
            call.respondText(processAndRenderUnsubscription(code), ContentType.Text.Html)
        }
    }
}

private suspend fun processAndRenderUnsubscription(code: String): String {
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
