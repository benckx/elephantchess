package io.elephantchess.webapp.ops

import io.elephantchess.servicelayer.model.TokenVerificationResult
import io.elephantchess.servicelayer.services.TokenManager
import io.elephantchess.servicelayer.utils.ops.koin
import io.ktor.server.application.*
import io.ktor.server.routing.*

private val tokenManager by koin<TokenManager>()

fun RoutingContext.extractAndVerifyToken(): TokenVerificationResult? =
    call.extractAndVerifyToken()

fun ApplicationCall.extractAndVerifyToken(allowCookies: Boolean = true): TokenVerificationResult? =
    token(allowCookies)?.let { token -> tokenManager.verifyToken(token) }

/**
 * Extract token from header or cookies
 */
private fun ApplicationCall.token(allowCookies: Boolean): String? {
    fun findFromHeaders(): String? =
        request.headers["Authorization"]?.let { header ->
            val split = header.split(' ')
            if (split.size != 2 || split.first() != "Bearer") {
                null
            } else {
                split.last()
            }
        }

    fun findFromCookie(name: String): String? =
        request.cookies[name]?.let { token -> token.ifBlank { null } }

    return findFromHeaders()
        ?: if (allowCookies) findFromCookie("user.token") ?: findFromCookie("guest.user.token") else null
}
