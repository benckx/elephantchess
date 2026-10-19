package io.elephantchess.webapp.server

import io.elephantchess.servicelayer.dto.ValidationErrorsResponse
import io.elephantchess.servicelayer.exceptions.HttpErrorException
import io.elephantchess.servicelayer.services.ExceptionService
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.utils.ResourceUtils
import io.elephantchess.webapp.ops.respondHtml
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.http.ContentType.Text.Html
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import java.io.IOException
import java.nio.channels.ClosedChannelException
import kotlin.coroutines.cancellation.CancellationException

private const val MUTED_URI_PATTERNS_RESOURCE = "/config/muted-uri-patterns.txt"

/**
 * Substring patterns (lower-cased) for 404 URIs that are only ever hit by vulnerability scanners or
 * benign automatic client probes. Requests matching one of these are logged at DEBUG instead of WARN
 * to keep the logs readable. Maintained in [MUTED_URI_PATTERNS_RESOURCE] rather than hard-coded here.
 */
private val MUTED_URI_PATTERNS: List<String> by lazy {
    ResourceUtils
        .resourceAsLines(MUTED_URI_PATTERNS_RESOURCE)
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .map { it.lowercase() }
}

/**
 * Checks if an exception is caused by the client disconnecting,
 * these exceptions should be logged at debug level and not trigger error responses.
 */
internal fun isClientDisconnectException(t: Throwable): Boolean {
    var current: Throwable? = t
    while (current != null) {
        if (current is ClosedChannelException ||
            current is ClosedByteChannelException ||
            current is CancellationException ||
            (current is IOException && current.message?.contains("Broken pipe") == true) ||
            (current is IOException && current.message?.contains("Connection reset") == true) ||
            (current is UnsupportedOperationException && current.message?.contains("response was already completed") == true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

/**
 * Ktor Netty throws `NullPointerException: Cannot invoke "RequestBodyHandler.upgrade()" because "bodyHandler" is null`
 * when the client closes the connection during a WebSocket upgrade (e.g. page left right after load, often headless
 * scrapers), as the Netty channel pipeline is already gone. The status has already been set to 101 at that point.
 *
 * The exception message can't be relied on: once the code is JIT-compiled, the JVM throws a pre-allocated NPE
 * without message nor stack trace (`-XX:+OmitStackTraceInFastThrow`, enabled by default).
 */
internal fun isClientDisconnectDuringWebSocketUpgrade(call: ApplicationCall, t: Throwable): Boolean =
    t is NullPointerException && call.response.status() == HttpStatusCode.SwitchingProtocols

/**
 * Ktor's static-content resolver throws `BadRequestException: Relative path should not contain path
 * traversing characters: ...` when a request tries to escape the static root (e.g. a scanner probing
 * `cache/original/../../.env`). These are vulnerability-scanner noise, so log them at DEBUG instead of
 * ERROR and don't persist them.
 */
internal fun isPathTraversalProbe(t: Throwable): Boolean =
    t.message?.contains("path traversing characters") == true

fun Application.exceptionHandler() {
    val logger = KotlinLogging.logger {}
    val exceptionService by koin<ExceptionService>()
    val simplePageRenderer by koin<SimplePageRenderer>()
    val webSocketUpgradeFailurePhase = PipelinePhase("WebSocketUpgradeFailure")

    // StatusPages rethrows exceptions when no response was sent (always the case for a failed WebSocket upgrade),
    // then Ktor logs them at ERROR level. Swallow the WebSocket upgrade NPE here: the client is gone anyway.
    // The phase is inserted before installing StatusPages, so it wraps the StatusPages phase.
    insertPhaseBefore(ApplicationCallPipeline.Setup, webSocketUpgradeFailurePhase)
    intercept(webSocketUpgradeFailurePhase) {
        try {
            proceed()
        } catch (e: NullPointerException) {
            if (!isClientDisconnectDuringWebSocketUpgrade(call, e)) throw e
            logger.debug { "client disconnected during WebSocket upgrade: ${call.request.uri}" }
        }
    }

    // Renders the proper 404 response: the HTML template for browsers (clients whose Accept header
    // prefers text/html), a JSON error payload for API clients / bots.
    suspend fun ApplicationCall.respondNotFound(uri: String) {
        val acceptsHtml = request.acceptItems().any { it.value == Html.toString() }
        if (acceptsHtml) {
            respondHtml(simplePageRenderer.renderTemplate("404"), HttpStatusCode.NotFound)
        } else {
            respond(HttpStatusCode.NotFound, ValidationErrorsResponse("Not Found: $uri"))
        }
    }

    // Generic 500 response for unexpected exceptions: the HTML error page for browsers, a JSON error payload
    // otherwise. Never exposes the exception message to the client.
    suspend fun ApplicationCall.respondServerError() {
        if (response.isCommitted) return
        val acceptsHtml = request.acceptItems().any { it.value == Html.toString() }
        if (acceptsHtml) {
            respondHtml(simplePageRenderer.renderTemplate("500"), InternalServerError)
        } else {
            respond(InternalServerError, ValidationErrorsResponse("Server error"))
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

            // Vulnerability-scanner noise and benign automatic client probes (native-app / passkey
            // association files, favicon variants, ...) always 404. They flood the logs, so mute them:
            // respond with a plain 404 and log at DEBUG. Patterns live in MUTED_URI_PATTERNS_RESOURCE.
            val path = uri.substringBefore('?').lowercase()
            if (MUTED_URI_PATTERNS.any { path.contains(it) }) {
                logger.debug { "Ignoring muted 404 probe: $method $uri | User-Agent: ${userAgent?.take(100)}" }
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
            } else if (isPathTraversalProbe(e)) {
                logger.debug { "Ignoring path-traversal probe: ${call.request.uri}" }
                call.respond(BadRequest, ValidationErrorsResponse("Bad Request"))
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
        exception<Throwable> { call, t ->
            // unlikely to reach here for client disconnects due to the specific handlers
            if (isClientDisconnectException(t) || isClientDisconnectDuringWebSocketUpgrade(call, t)) {
                logger.debug { "Client disconnected during request: ${t.message}" }
            } else {
                logger.error(t) { t.toString() }
                exceptionService.saveException(t, InternalServerError.value)
                call.respondServerError()
            }
        }
    }
}
