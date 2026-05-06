package io.elephantchess.webapp.ops

import io.elephantchess.servicelayer.model.TokenVerificationResult
import io.elephantchess.servicelayer.services.TokenManager
import io.elephantchess.servicelayer.utils.ops.koin
import io.ktor.server.application.*
import io.ktor.server.routing.*

private val tokenManager by koin<TokenManager>()

fun RoutingContext.extractAndVerifyToken(): TokenVerificationResult? {
    return call.token()?.let { token -> tokenManager.verifyToken(token) }
}

/**
 * Extract token from header or cookies
 */
private fun ApplicationCall.token(): String? {
    fun findFromHeaders(): String? {
        return request.headers["Authorization"]?.let { header ->
            val split = header.split(' ')
            if (split.size != 2 || split.first() != "Bearer") {
                null
            } else {
                split.last()
            }
        }
    }

    fun findFromCookie(name: String): String? =
        request.cookies[name]?.let { token -> token.ifBlank { null } }

    return findFromHeaders()
        ?: findFromCookie("user.token")
        ?: findFromCookie("guest.user.token")
}
