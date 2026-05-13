package io.elephantchess.webapp.routing

import io.elephantchess.servicelayer.services.UserService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val simplePageRenderer by koin<SimplePageRenderer>()
private val userService by koin<UserService>()

// renders a public page when the user clicks the confirmation link sent to them by email at signup.
fun Application.emailConfirmationRoutes() {
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
