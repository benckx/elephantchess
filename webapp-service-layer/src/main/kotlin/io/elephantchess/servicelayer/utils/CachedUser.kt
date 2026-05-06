package io.elephantchess.servicelayer.utils

import io.elephantchess.model.UserRole
import io.elephantchess.model.UserType
import kotlin.time.Instant

data class CachedUser(
    val userType: UserType,
    val userId: String,
    val username: String,
    val creationTime: Instant,
    val roles: List<UserRole>
)
