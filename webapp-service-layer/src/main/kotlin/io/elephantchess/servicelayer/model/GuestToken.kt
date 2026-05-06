package io.elephantchess.servicelayer.model

import com.auth0.jwt.interfaces.DecodedJWT
import io.elephantchess.model.UserType

data class GuestToken(override val userId: String, val decoded: DecodedJWT) : VerifiedToken(decoded) {

    override fun userId(): UserId =
        UserId(UserType.GUEST, userId)

    override fun toString(): String {
        val attributes = mutableListOf<Pair<String, String>>()
        attributes += "userId" to userId

        expiresAtInstant()?.let { expiresAt ->
            attributes += "expiresAt" to expiresAt.toString()
        }

        return "GuestToken{${attributes.joinToString(", ") { (k, v) -> "$k=$v" }}}"
    }

}
