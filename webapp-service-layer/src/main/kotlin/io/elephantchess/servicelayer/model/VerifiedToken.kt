package io.elephantchess.servicelayer.model

import com.auth0.jwt.interfaces.DecodedJWT
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

abstract class VerifiedToken(
    private val decodedJWT: DecodedJWT,
) : TokenVerificationResult {

    abstract val userId: String
    abstract fun userId(): UserId

    fun expiresAtInstant(): Instant? {
        return decodedJWT.expiresAt?.toInstant()?.toKotlinInstant()
    }

    fun expiresAtInDays(): Long? {
        val expiresAt = expiresAtInstant() ?: return null
        return (expiresAt - Clock.System.now()).inWholeDays
    }

}
