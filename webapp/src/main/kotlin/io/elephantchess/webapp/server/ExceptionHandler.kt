package io.elephantchess.webapp.server

import io.elephantchess.servicelayer.dto.ValidationErrorsResponse
import io.elephantchess.servicelayer.exceptions.HttpErrorException
import io.elephantchess.servicelayer.services.ExceptionService
import io.elephantchess.servicelayer.utils.ops.koin
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import java.io.IOException
import java.nio.channels.ClosedChannelException

fun Application.exceptionHandler() {
    val logger = KotlinLogging.logger {}
    val exceptionService by koin<ExceptionService>()

    install(StatusPages) {
        // handle 404 Not Found - log unmapped URIs
        status(HttpStatusCode.NotFound) { call, status ->
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

            logger.warn { "UNMAPPED URI: $method $uri | Content-Type: $contentType | User-Agent: ${userAgent?.take(100)}" }
            call.respond(status, ValidationErrorsResponse("Not Found: $uri"))
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
