package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.UserType
import io.elephantchess.servicelayer.dto.user.EmailValidityStatus

data class AdminUserInsightsMetadata(
    val userId: String,
    val username: String,
    val email: String?,
    val userType: UserType?,
    val country: String?,
    val creationTime: Long?,
    val lastOnlineTime: Long?,
    val lastProfileUpdateTime: Long?,
    val emailConfirmedAtTime: Long?,
    val emailValidityStatus: EmailValidityStatus?,
    val hasRoleAdmin: Boolean,
    val hasRoleEditor: Boolean,
)
