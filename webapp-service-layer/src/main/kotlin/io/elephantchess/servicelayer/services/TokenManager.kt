package io.elephantchess.servicelayer.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.elephantchess.config.AppConfig
import io.elephantchess.db.dao.codegen.tables.pojos.User
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.db.utils.roles
import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.exceptions.UnauthorizedException
import io.elephantchess.servicelayer.model.AuthenticatedToken
import io.elephantchess.servicelayer.model.AuthenticatedToken.Companion.ROLES_CLAIM
import io.elephantchess.servicelayer.model.AuthenticatedToken.Companion.USERNAME_CLAIM
import io.elephantchess.servicelayer.model.GuestToken
import io.elephantchess.servicelayer.model.InvalidToken
import io.elephantchess.servicelayer.model.TokenVerificationResult
import io.github.oshai.kotlinlogging.KLogger
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.minutes

class TokenManager(
    private val appConfig: AppConfig,
    private val userDaoService: UserDaoService,
    private val logger: KLogger,
) {

    private val isUserAllowedCache =
        Cache
            .Builder<String, Boolean>()
            .expireAfterWrite(15.minutes)
            .build()

    private val algorithm by lazy {
        logger.warn { "initializing algorithm for profile ${appConfig.profile}" }
        val symmetricKey = appConfig.symmetricKey
        Algorithm.HMAC256(symmetricKey)
    }

    private val verifier by lazy {
        logger.warn { "initializing verifier for profile ${appConfig.profile}" }
        JWT.require(algorithm).withIssuer(ISSUER).build()
    }

    fun buildTokenForAuthenticatedUser(user: User): String {
        val tokenBuilder =
            JWT
                .create()
                .withAudience(user.id)
                .withIssuer(ISSUER)
                .withClaim(USERNAME_CLAIM, user.handle)
                .withClaim(ROLES_CLAIM, user.roles.map { role -> role.name })
                .withClaim(TOKEN_TYPE_CLAIM, UserType.AUTHENTICATED.name)
                .withExpiresAt(Instant.now().plus(TOKEN_TTL_DAYS, ChronoUnit.DAYS))

        return tokenBuilder.sign(algorithm)
    }

    fun buildTokenForGuestUser(userId: String): String {
        val tokenBuilder =
            JWT
                .create()
                .withAudience(userId)
                .withIssuer(ISSUER)
                .withClaim(TOKEN_TYPE_CLAIM, UserType.GUEST.name)
                .withExpiresAt(Instant.now().plus(ANONYMOUS_TOKEN_TTL_DAYS, ChronoUnit.DAYS))

        return tokenBuilder.sign(algorithm)
    }

    fun verifyToken(token: String): TokenVerificationResult {
        return try {
            val decodedJWT = verifier.verify(token)
            val tokenType = decodedJWT.getClaim(TOKEN_TYPE_CLAIM)
                ?.`as`(String::class.java)
                ?.let { UserType.valueOf(it) }

            val userId = decodedJWT.audience.first()
            val allowed = runBlocking {
                isUserAllowedCache.get(userId) {
                    userDaoService.existsById(userId)
                }
            }

            if (!allowed) {
                logger.warn { "user $userId is not allowed or not found" }
                InvalidToken(UnauthorizedException("user $userId is not allowed or not found"))
            } else {
                when (tokenType) {
                    UserType.AUTHENTICATED, null -> AuthenticatedToken(userId, decodedJWT)
                    UserType.GUEST -> GuestToken(userId, decodedJWT)
                }
            }
        } catch (e: Exception) {
            InvalidToken(e)
        }
    }

    companion object {

        const val ISSUER = "elephantchess.io"
        const val TOKEN_TTL_DAYS = 400L
        const val ANONYMOUS_TOKEN_TTL_DAYS = 30L
        const val RENEW_SESSION_INTERVAL = 300L

        const val TOKEN_TYPE_CLAIM = "TOKEN_TYPE"

    }

}
