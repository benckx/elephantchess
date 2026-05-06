package io.elephantchess.servicelayer.dto.admin

import io.elephantchess.model.UserType

data class LastChatMessageResponse(val messages: List<ChatMessage>) {

    data class ChatMessage(
        val gameId: String,
        val index: Int,
        val author: Author,
        val messageTime: Long,
        val content: String,
    )

    data class Author(
        val userType: UserType,
        val userId: String,
        val username: String
    )

}
