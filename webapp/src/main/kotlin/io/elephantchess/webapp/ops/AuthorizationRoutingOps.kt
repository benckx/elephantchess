package io.elephantchess.webapp.ops

import io.elephantchess.model.UserRole
import io.elephantchess.model.UserRole.ADMIN
import io.elephantchess.model.UserRole.EDITOR
import io.elephantchess.model.UserType.AUTHENTICATED
import io.elephantchess.model.UserType.GUEST
import io.elephantchess.servicelayer.model.AuthenticatedToken
import io.elephantchess.servicelayer.model.GuestToken
import io.elephantchess.servicelayer.model.TokenVerificationResult
import io.elephantchess.servicelayer.model.VerifiedToken
import io.elephantchess.servicelayer.services.UserCache
import io.elephantchess.servicelayer.utils.ops.koin
import io.elephantchess.webapp.rendering.SimplePageRenderer
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.http.*
import io.ktor.http.ContentType.Text.Html
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

private val opsLogger by lazy { logger {} }
private val simplePageRenderer by koin<SimplePageRenderer>()
private val userCache by koin<UserCache>()

/**
 * Require an authenticated user (not a guest)
 */
suspend fun RoutingContext.requireAuthentication(
    handler: suspend (AuthenticatedToken) -> Any
) {
    val result = extractAndVerifyToken()
    if (result is AuthenticatedToken && verifyInDb(result)) {
        call.respond(handler(result))
    } else {
        handleInvalidToken(result, "authentication", Forbidden)
    }
}

/**
 * Require an authenticated user (not a guest)
 * Same as above but with a request body
 */
suspend inline fun <reified T : Any> RoutingContext.requireAuthenticationWithBody(
    handler: suspend (AuthenticatedToken, T) -> Any
) {
    val result = extractAndVerifyToken()
    if (result is AuthenticatedToken && verifyInDb(result)) {
        call.respond(handler(result, call.receive<T>()))
    } else {
        handleInvalidToken(result, "authentication", Forbidden)
    }
}

/**
 * Require an authenticated user or a guest user
 */
suspend fun RoutingContext.requireIdentification(
    handler: suspend (VerifiedToken) -> Any
) {
    val result = extractAndVerifyToken()
    if (result is VerifiedToken && verifyInDb(result)) {
        call.respond(handler(result))
    } else {
        handleInvalidToken(result, "identification", Unauthorized)
    }
}

/**
 * Require an authenticated user or a guest user
 * Same as above but with a request body
 */
suspend inline fun <reified T : Any> RoutingContext.requireIdentificationWithBody(
    handler: (VerifiedToken, T) -> Any
) {
    val result = extractAndVerifyToken()
    if (result is VerifiedToken && verifyInDb(result)) {
        call.respond(handler(result, call.receive<T>()))
    } else {
        handleInvalidToken(result, "identification", Unauthorized)
    }
}

suspend fun RoutingContext.requireAdminRole(
    customErrorPage: (suspend () -> String)? = null,
    handler: suspend (AuthenticatedToken) -> Any
) {
    requireRole(ADMIN, handler, customErrorPage)
}

suspend fun RoutingContext.requireEditorRole(
    customErrorPage: (suspend () -> String)? = null,
    handler: suspend (AuthenticatedToken) -> Any
) {
    requireRole(EDITOR, handler, customErrorPage)
}

private suspend fun RoutingContext.requireRole(
    requiredRole: UserRole,
    handler: suspend (AuthenticatedToken) -> Any,
    customErrorPage: (suspend () -> String)?,
) {
    val result = extractAndVerifyToken()
    if (result is AuthenticatedToken && result.hasRole(requiredRole) && verifyInDb(result, requiredRole)) {
        call.respond(handler(result))
    } else if (customErrorPage != null) {
        call.respond(TextContent(customErrorPage(), Html))
    } else {
        handleInvalidToken(result, requiredRole.name, Forbidden)
    }
}

suspend fun RoutingContext.handleInvalidToken(
    result: TokenVerificationResult?,
    permission: String,
    statusCode: HttpStatusCode
) {
    val headersStr =
        call.request.headers
            .toMap()
            .filterNot { (key, _) -> key == "Authorization" || key == "Cookie" }
            .map { header -> header.key + " = " + header.value }
            .joinToString(", ")

    val cookiesStr =
        call.request.cookies.rawCookies
            .toMap()
            .filterNot { (key, _) -> key == "user.token" || key == "guest.user.token" }
            .map { cookie -> cookie.key + " = " + cookie.value }
            .joinToString(", ")

    opsLogger.warn { "[requested:$permission] received $result for ${call.request.uri}, accept ${call.request.accept()}, $headersStr, $cookiesStr" }
    redirectOrRespond(statusCode)
}

private suspend fun RoutingContext.redirectOrRespond(statusCode: HttpStatusCode) {
    val isHtmlRequest = call.request.accept()?.contains("text/html") ?: false
    if (isHtmlRequest) {
        val html = simplePageRenderer.renderTemplate(statusCode.value.toString())
        call.respondText(html, Html)
    } else {
        call.response.status(statusCode)
    }
}

suspend fun verifyInDb(token: TokenVerificationResult, requiredRole: UserRole? = null): Boolean {

    return when (token) {
        is AuthenticatedToken -> {
            val user = userCache.get(token.userId)
            user != null && user.userType == AUTHENTICATED &&
                    (requiredRole == null || user.roles.contains(requiredRole))
        }

        is GuestToken -> {
            val user = userCache.get(token.userId)
            user != null && user.userType == GUEST &&
                    (requiredRole == null || user.roles.contains(requiredRole))
        }

        else -> false
    }
}
