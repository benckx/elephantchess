package io.elephantchess.webapp.ops

import io.elephantchess.servicelayer.dto.ValidatedResponse
import io.elephantchess.utils.GenericEither
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NotAcceptable
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

suspend inline fun <reified T : Any, reified B : Any> RoutingContext.handleValidatedResponse(
    handler: (T) -> ValidatedResponse<B>
) {
    handleEither(handler)
}

suspend inline fun <reified T : Any, reified A : Any, reified B : Any> RoutingContext.handleEither(
    handler: (T) -> GenericEither<A, B>,
) {
    val request = call.receive<T>()
    val either = handler(request)
    if (either.isRight()) {
        call.respond(Created, either.right())
    } else {
        call.respond(NotAcceptable, either.left())
    }
}
