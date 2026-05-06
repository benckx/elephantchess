package io.elephantchess.servicelayer.model

import com.auth0.jwt.interfaces.DecodedJWT
import io.elephantchess.model.UserRole
import io.elephantchess.model.UserType

data class AuthenticatedToken(
    override val userId: String,
    val decoded: DecodedJWT
) : VerifiedToken(decoded) {

    fun username(): String? {
        return decoded.getClaim(USERNAME_CLAIM)?.asString()
    }

    fun hasRole(role: UserRole): Boolean {
        return roles().contains(role)
    }

    override fun userId(): UserId =
        UserId(UserType.AUTHENTICATED, userId)

    private fun roles(): List<UserRole> {
        return decoded
            .getClaim(ROLES_CLAIM)
            ?.asList(String::class.java)
            ?.map { role -> UserRole.valueOf(role) }
            .orEmpty()
    }

    override fun toString(): String {
        val attributes = mutableListOf<Pair<String, String>>()

        attributes += "userId" to userId
        username()?.let { username ->
            attributes += "username" to username
        }
        roles().let { roles ->
            if (roles.isNotEmpty()) {
                attributes += "roles" to roles.joinToString(", ")
            }
        }
        expiresAtInstant()?.let { expiresAt ->
            attributes += "expiresAt" to expiresAt.toString()
        }

        return "AuthenticatedToken{${attributes.joinToString(", ") { (k, v) -> "$k=$v" }}}"
    }

    companion object {

        const val USERNAME_CLAIM = "USERNAME"
        const val ROLES_CLAIM = "ROLES"

    }

}
