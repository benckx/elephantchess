package io.elephantchess.servicelayer.dto

import io.elephantchess.model.UserType

data class ChatMessage(
    val index: Int,
    val author: Author,
    val messageTime: Long,
    val content: String,
) {

    data class Author(
        val userType: UserType,
        val userId: String,
        val username: String
    )

}

