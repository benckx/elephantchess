package io.elephantchess.servicelayer.model

import io.elephantchess.model.UserType
import org.apache.commons.lang3.StringUtils.capitalize

data class UserId(
    val userType: UserType,
    val id: String
) {

    override fun toString() = "${capitalize(userType.name.lowercase())}($id)"

}
