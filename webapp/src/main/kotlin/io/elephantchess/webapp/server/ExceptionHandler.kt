package io.elephantchess.webapp.server

import io.elephantchess.servicelayer.dto.ValidationErrorsResponse
import io.elephantchess.servicelayer.exceptions.HttpErrorException
import io.elephantchess.servicelayer.services.ExceptionService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.http.ContentType.Text.Html
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.content.TextContent
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import java.io.IOException
import java.nio.channels.ClosedChannelException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Well-known URIs that clients (mobile OSes, password managers, ...) probe automatically. We don't
 * ship native apps or passkeys, so these always 404 - mute them to avoid noisy WARN logs.
 */
private val MUTED_WELL_KNOWN_URIS = setOf(
    "/.well-known/assetlinks.json",
    "/.well-known/apple-app-site-association",
    "/apple-app-site-association",
    "/.well-known/passkey-endpoints",
)

fun Application.exceptionHandler() {
    val logger = KotlinLogging.logger {}
    val exceptionService by koin<ExceptionService>()
    val simplePageRenderer by koin<SimplePageRenderer>()

    // Renders the proper 404 response: the HTML template for browsers (clients whose Accept header
    // prefers text/html), a JSON error payload for API clients / bots.
    suspend fun ApplicationCall.respondNotFound(uri: String) {
        val acceptsHtml = request.acceptItems().any { it.value == Html.toString() }
        if (acceptsHtml) {
            respond(TextContent(simplePageRenderer.renderTemplate("404"), Html, HttpStatusCode.NotFound))
        } else {
            respond(HttpStatusCode.NotFound, ValidationErrorsResponse("Not Found: $uri"))
        }
    }

    install(StatusPages) {
        // handle 404 Not Found - log unmapped URIs
        status(HttpStatusCode.NotFound) { call, _ ->
            val method = call.request.httpMethod.value
            val uri = call.request.uri
            val userAgent = call.request.headers["User-Agent"]
            val contentType = call.request.headers["Content-Type"]

            // Plain HTTP requests hitting a WebSocket endpoint (i.e. without a valid `Upgrade: websocket`
            // header) fall through to here. This typically happens when an intermediary (corporate proxy,
            // antivirus, mobile carrier, ...) strips the Upgrade header. Don't spam WARN logs for those:
            // respond with 426 Upgrade Required and log at debug level.
            if (uri.startsWith("/ws/")) {
                logger.debug {
                    "WebSocket path called without Upgrade header: $method $uri | User-Agent: ${userAgent?.take(100)}"
                }
                call.respond(HttpStatusCode.UpgradeRequired, ValidationErrorsResponse("WebSocket upgrade required"))
                return@status
            }

            // Native-app / passkey association probes hit these standard well-known URIs automatically
            // (Android App Links, iOS Universal Links, WebAuthn passkey discovery). We don't ship native
            // apps or passkeys, so a 404 is the correct response - just don't spam WARN logs for them.
            if (uri.substringBefore('?') in MUTED_WELL_KNOWN_URIS) {
                logger.debug { "Ignoring well-known probe: $method $uri | User-Agent: ${userAgent?.take(100)}" }
                call.respondNotFound(uri)
                return@status
            }

            logger.warn { "UNMAPPED URI: $method $uri | Content-Type: $contentType | User-Agent: ${userAgent?.take(100)}" }
            call.respondNotFound(uri)
        }

        // handle client disconnection exceptions silently
        // nothing we can do if the client is gone, no need to store it
        exception<ClosedChannelException> { _, e ->
            logger.debug { "client disconnected: ${e.message}" }
        }
        exception<ClosedWriteChannelException> { _, e ->
            logger.debug { "client disconnected: ${e.message}" }
        }
        exception<ClosedByteChannelException> { _, e ->
            logger.debug { "client disconnected: ${e.message}" }
        }
        exception<HttpErrorException> { call, e ->
            logger.debug { e.toString() }
            exceptionService.saveException(e, e.code)
            call.respond(HttpStatusCode.fromValue(e.code), ValidationErrorsResponse(e))
        }
        exception<IllegalArgumentException> { call, e ->
            logger.error(e) { e.toString() }
            exceptionService.saveException(e, BadRequest.value)
            call.respond(BadRequest, ValidationErrorsResponse("Bad Request"))
        }
        exception<IllegalStateException> { call, e ->
            logger.error(e) { e.toString() }
            exceptionService.saveException(e, InternalServerError.value)
            call.respond(InternalServerError, ValidationErrorsResponse("Server error"))
        }
        exception<io.ktor.server.plugins.BadRequestException> { call, e ->
            if (e.cause is JsonConvertException) {
                logger.error(e) { "JSON error: ${e.cause}" }
                exceptionService.saveException(e, BadRequest.value)
                call.respond(BadRequest, ValidationErrorsResponse("Bad Request: JSON error"))
            } else {
                logger.error(e) { e.toString() }
                exceptionService.saveException(e, BadRequest.value)
                call.respond(BadRequest, ValidationErrorsResponse("Bad Request"))
            }
        }
        exception<io.ktor.server.plugins.ContentTransformationException> { call, e ->
            logger.error(e) { e.toString() }
            exceptionService.saveException(e, BadRequest.value)
            call.respond(BadRequest, ValidationErrorsResponse("Bad Request: Content Transformation"))
        }
        exception<Throwable> { _, t ->
            // checks if an exception is caused by the client disconnecting,
            // these exceptions should be logged at debug level and not trigger error responses.
            fun isClientDisconnectException(t: Throwable): Boolean {
                var current: Throwable? = t
                while (current != null) {
                    if (current is ClosedChannelException ||
                        current is ClosedByteChannelException ||
                        current is CancellationException ||
                        current is IOException && current.message?.contains("Broken pipe") == true ||
                        current is IOException && current.message?.contains("Connection reset") == true ||
                        current is UnsupportedOperationException && current.message?.contains("response was already completed") == true
                    ) {
                        return true
                    }
                    current = current.cause
                }
                return false
            }

            // unlikely to reach here for client disconnects due to the specific handlers
            if (isClientDisconnectException(t)) {
                logger.debug { "Client disconnected during request: ${t.message}" }
            } else {
                logger.error(t) { t.toString() }
                exceptionService.saveException(t, InternalServerError.value)
            }
        }
    }
}
